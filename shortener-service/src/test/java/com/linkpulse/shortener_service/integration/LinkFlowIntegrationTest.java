package com.linkpulse.shortener_service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LinkFlowIntegrationTest {

    private static final String LONG_URL = "https://example.com/products/shoes/nike/air-max?color=red";

    @Container
    @ServiceConnection                       // Spring auto-points the datasource at this container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    // do NOT follow redirects, so we can inspect the 302 itself
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private String token;

    @BeforeEach
    void registerAndLogin() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        String creds = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";

        assertEquals(201, postJson("/auth/register", creds, null).statusCode());

        HttpResponse<String> login = postJson("/auth/login", creds, null);
        assertEquals(200, login.statusCode());
        Matcher m = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(login.body());
        assertTrue(m.find(), "no accessToken in response: " + login.body());
        token = m.group(1);
    }

    @Test
    void createWithoutToken_returns401() throws Exception {
        HttpResponse<String> res = postJson("/v1/links", "{\"longUrl\":\"" + LONG_URL + "\"}", null);

        assertEquals(401, res.statusCode());
        assertTrue(res.body().contains("UNAUTHORIZED"));
    }

    @Test
    void createThenRedirect_returns302WithLocation() throws Exception {
        HttpResponse<String> created = post("{\"longUrl\":\"" + LONG_URL + "\"}");
        assertEquals(201, created.statusCode());

        String code = extractCode(created.body());

        HttpResponse<String> redirect = get("/" + code);
        assertEquals(302, redirect.statusCode());
        assertEquals(LONG_URL, redirect.headers().firstValue("Location").orElse(""));
    }

    @Test
    void unknownCode_returns404InStandardFormat() throws Exception {
        HttpResponse<String> res = get("/doesnotexist");

        assertEquals(404, res.statusCode());
        assertTrue(res.body().contains("NOT_FOUND"));
    }

    @Test
    void duplicateAlias_returns409() throws Exception {
        String body = "{\"longUrl\":\"" + LONG_URL + "\",\"alias\":\"it-dup-1\"}";

        assertEquals(201, post(body).statusCode());
        assertEquals(409, post(body).statusCode());
    }

    @Test
    void expiredLink_returns410() throws Exception {
        String body = "{\"longUrl\":\"" + LONG_URL + "\",\"alias\":\"it-old-1\"}";
        assertEquals(201, post(body).statusCode());

        // @Future blocks past dates via the API, so age the row directly in the DB
        jdbc.update("UPDATE links SET expires_at = NOW() - INTERVAL '1 day' WHERE code = ?", "it-old-1");

        assertEquals(410, get("/it-old-1").statusCode());
    }

    // ---------- helpers ----------

    private HttpResponse<String> post(String json) throws Exception {
        return postJson("/v1/links", json, token);
    }

    private HttpResponse<String> postJson(String path, String json, String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String extractCode(String json) {
        Matcher m = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertTrue(m.find(), "no code in response: " + json);
        return m.group(1);
    }
}