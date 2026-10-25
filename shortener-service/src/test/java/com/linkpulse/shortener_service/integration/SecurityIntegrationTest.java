package com.linkpulse.shortener_service.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.linkpulse.shortener_service.entity.Link;
import com.linkpulse.shortener_service.entity.User;
import com.linkpulse.shortener_service.repository.LinkRepository;
import com.linkpulse.shortener_service.repository.UserRepository;
import com.linkpulse.shortener_service.security.JwtService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String CREATE_BODY = "{\"longUrl\":\"https://example.com/some/page\"}";

    @Container
    @ServiceConnection                       // Spring auto-points the datasource at this container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private LinkRepository linkRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private User alice;
    private String aliceToken;
    private String bobToken;
    private String adminToken;

    @BeforeEach
    void seed() {
        // links first: they reference users via the owner FK
        linkRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(new User("alice@example.com", passwordEncoder.encode(PASSWORD)));
        User bob = userRepository.save(new User("bob@example.com", passwordEncoder.encode(PASSWORD)));

        User admin = new User("admin@example.com", passwordEncoder.encode(PASSWORD));
        ReflectionTestUtils.setField(admin, "role", "ADMIN");   // no setter exists, and none should be added just for tests
        admin = userRepository.save(admin);

        aliceToken = jwtService.generateToken(alice);
        bobToken = jwtService.generateToken(bob);
        adminToken = jwtService.generateToken(admin);
    }

    // ---------- 401: unauthenticated ----------

    @Test
    void createWithoutToken_returns401() throws Exception {
        mvc.perform(post("/v1/links").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void createWithGarbageToken_returns401() throws Exception {
        // the filter swallows the parse error and stays anonymous, so the entry point must still answer 401
        mvc.perform(post("/v1/links").header("Authorization", "Bearer not.a.jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void createWithExpiredToken_returns401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String expired = Jwts.builder()
                .subject(alice.getEmail())
                .claim("uid", alice.getId())
                .claim("role", "USER")
                .issuedAt(Date.from(now.minusSeconds(7200)))
                .expiration(Date.from(now.minusSeconds(3600)))   // properly signed, but already expired
                .signWith(key)
                .compact();

        mvc.perform(post("/v1/links").header("Authorization", "Bearer " + expired)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedAdminToken_returns401() throws Exception {
        // privilege-escalation attempt: edit the payload to ADMIN but keep alice's original signature
        String[] parts = aliceToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tampered = payload.replace("\"role\":\"USER\"", "\"role\":\"ADMIN\"");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tampered.getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        // 401 (not 403): the signature no longer matches, so the token must be rejected outright
        mvc.perform(patch("/v1/admin/links/x/disable").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointWithoutToken_returns401() throws Exception {
        mvc.perform(patch("/v1/admin/links/x/disable"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- public endpoints ----------

    @Test
    void redirectToUnknownCode_isPublicAndReturns404() throws Exception {
        // 404 (not 401) proves the route is reachable without a token
        mvc.perform(get("/doesnotexist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void login_isPublicAndReturnsAccessToken() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // ---------- 403: authenticated but not allowed ----------

    @Test
    void adminEndpointWithUserToken_returns403() throws Exception {
        mvc.perform(patch("/v1/admin/links/x/disable").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deleteOtherUsersLink_returns403() throws Exception {
        linkRepository.save(new Link("alice1", "https://example.com/a", alice.getId()));

        mvc.perform(delete("/v1/links/alice1").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // ---------- allowed ----------

    @Test
    void createWithValidToken_returns201() throws Exception {
        mvc.perform(post("/v1/links").header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void deleteOwnLink_returns204() throws Exception {
        linkRepository.save(new Link("alice2", "https://example.com/b", alice.getId()));

        mvc.perform(delete("/v1/links/alice2").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminDisablesLink_returns204() throws Exception {
        linkRepository.save(new Link("alice3", "https://example.com/c", alice.getId()));

        // admin does not own the link, so success proves the role check, not ownership, grants access
        mvc.perform(patch("/v1/admin/links/alice3/disable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
