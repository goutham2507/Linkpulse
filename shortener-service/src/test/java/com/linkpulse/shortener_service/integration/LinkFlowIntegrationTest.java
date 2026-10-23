package com.linkpulse.shortener_service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/links"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
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