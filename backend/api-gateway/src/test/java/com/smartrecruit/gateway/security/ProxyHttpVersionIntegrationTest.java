package com.smartrecruit.gateway.security;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the h2c regression: the gateway must talk plain HTTP/1.1 to the backends. The JDK client's default
 * cleartext upgrade (Upgrade: h2c + HTTP2-Settings) makes uvicorn answer 422 and parse the body as a new request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyHttpVersionIntegrationTest {

    private static final HttpServer DOWNSTREAM;
    private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();
    private static final Map<String, String> LAST_HEADERS = new ConcurrentHashMap<>();

    static {
        try {
            DOWNSTREAM = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        DOWNSTREAM.createContext("/", exchange -> {
            LAST_HEADERS.clear();
            exchange.getRequestHeaders().forEach((name, values) -> LAST_HEADERS.put(name.toLowerCase(), values.getFirst()));
            LAST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "downstream-ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        DOWNSTREAM.start();
    }

    @DynamicPropertySource
    static void routeEverythingToDownstream(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + DOWNSTREAM.getAddress().getPort();
        for (String service : List.of("auth-service", "user-service", "job-service", "application-service",
                "dashboard-service", "ai-service")) {
            registry.add("services." + service + ".uri", () -> uri);
        }
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @LocalServerPort
    private int port;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    // HTTP/1.1 client so any Upgrade header seen downstream was added by the gateway, not forwarded from here.
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private final UUID candidate = UUID.randomUUID();

    @BeforeEach
    void reset() {
        LAST_BODY.set(null);
        LAST_HEADERS.clear();
    }

    private String bearer() {
        return "Bearer " + Jwts.builder()
                .subject(candidate.toString())
                .claim("role", "CANDIDATE")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private HttpResponse<String> call(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .header("Authorization", bearer());
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void postWithBodyReachesDownstreamWithoutH2cUpgradeHeaders() throws Exception {
        String body = "{\"cvId\": \"" + candidate + "\", \"jobId\": \"" + UUID.randomUUID() + "\"}";

        HttpResponse<String> response = call("POST", "/api/v1/ai/matching/score", body);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(LAST_BODY.get()).isEqualTo(body);
        assertThat(LAST_HEADERS).doesNotContainKeys("upgrade", "http2-settings");
        assertThat(LAST_HEADERS.getOrDefault("connection", "")).doesNotContainIgnoringCase("upgrade");
    }

    @Test
    void getReachesDownstreamWithoutH2cUpgradeHeaders() throws Exception {
        HttpResponse<String> response = call("GET",
                "/api/v1/ai/skill-gap/candidates/" + candidate + "/jobs/" + UUID.randomUUID(), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(LAST_HEADERS).doesNotContainKeys("upgrade", "http2-settings");
        assertThat(LAST_HEADERS.getOrDefault("connection", "")).doesNotContainIgnoringCase("upgrade");
    }
}
