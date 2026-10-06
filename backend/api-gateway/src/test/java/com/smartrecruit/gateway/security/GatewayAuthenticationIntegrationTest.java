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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-SEC-04: JWT validated once at the gateway, identity forwarded in signed headers.
 * A JDK HTTP server plays the role of every downstream microservice and records what it receives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayAuthenticationIntegrationTest {

    private static final HttpServer DOWNSTREAM;
    private static final AtomicInteger HITS = new AtomicInteger();
    private static final Map<String, String> LAST_HEADERS = new ConcurrentHashMap<>();

    static {
        try {
            DOWNSTREAM = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        DOWNSTREAM.createContext("/", exchange -> {
            HITS.incrementAndGet();
            LAST_HEADERS.clear();
            exchange.getRequestHeaders().forEach((name, values) -> LAST_HEADERS.put(name.toLowerCase(), values.getFirst()));
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

    @Value("${security.internal-auth.secret}")
    private String internalSecret;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        HITS.set(0);
        LAST_HEADERS.clear();
    }

    private String jwt(UUID userId, String role, Instant expiresAt, String secret) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void requestWithoutTokenIsRejectedBeforeReachingTheService() throws Exception {
        HttpResponse<String> response = get("/api/v1/users/candidates/me", Map.of());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"status\":401").contains("/api/v1/users/candidates/me");
        assertThat(HITS.get()).isZero();
    }

    @Test
    void invalidOrExpiredTokensAreRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        List<String> badTokens = List.of(
                "not-a-jwt",
                jwt(userId, "CANDIDATE", Instant.now().minusSeconds(10), jwtSecret),
                jwt(userId, "CANDIDATE", Instant.now().plusSeconds(60), "another-secret-key-long-enough-for-hs256!!"),
                jwt(userId, "SUPERUSER", Instant.now().plusSeconds(60), jwtSecret));

        for (String token : badTokens) {
            assertThat(get("/api/v1/jobs", Map.of("Authorization", "Bearer " + token)).statusCode())
                    .isEqualTo(401);
        }
        assertThat(HITS.get()).isZero();
    }

    @Test
    void validTokenIsForwardedWithSignedIdentityHeaders() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwt(userId, "RECRUITER", Instant.now().plusSeconds(3600), jwtSecret);

        HttpResponse<String> response = get("/api/v1/jobs", Map.of("Authorization", "Bearer " + token));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("downstream-ok");
        assertThat(LAST_HEADERS.get("x-auth-user-id")).isEqualTo(userId.toString());
        assertThat(LAST_HEADERS.get("x-auth-user-role")).isEqualTo("RECRUITER");
        long timestamp = Long.parseLong(LAST_HEADERS.get("x-auth-timestamp"));
        assertThat(timestamp).isCloseTo(Instant.now().getEpochSecond(), org.assertj.core.data.Offset.offset(5L));
        assertThat(LAST_HEADERS.get("x-auth-signature"))
                .isEqualTo(InternalIdentityHeaders.sign(internalSecret, userId.toString(), "RECRUITER", timestamp));
    }

    @Test
    void spoofedIdentityHeadersAreReplacedByTheGateway() throws Exception {
        UUID realUser = UUID.randomUUID();
        String token = jwt(realUser, "CANDIDATE", Instant.now().plusSeconds(3600), jwtSecret);

        get("/api/v1/users/candidates/me", Map.of(
                "Authorization", "Bearer " + token,
                "X-Auth-User-Id", UUID.randomUUID().toString(),
                "X-Auth-User-Role", "ADMIN",
                "X-Auth-Signature", "forged"));

        assertThat(LAST_HEADERS.get("x-auth-user-id")).isEqualTo(realUser.toString());
        assertThat(LAST_HEADERS.get("x-auth-user-role")).isEqualTo("CANDIDATE");
        assertThat(LAST_HEADERS.get("x-auth-signature")).isNotEqualTo("forged");
    }

    @Test
    void publicAuthEndpointsDoNotNeedATokenAndCannotCarrySpoofedHeaders() throws Exception {
        HttpResponse<String> response = get("/api/v1/auth/login", Map.of(
                "X-Auth-User-Id", UUID.randomUUID().toString(),
                "X-Auth-User-Role", "ADMIN"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(HITS.get()).isEqualTo(1);
        assertThat(LAST_HEADERS).doesNotContainKeys("x-auth-user-id", "x-auth-user-role", "x-auth-signature");
    }

    @Test
    void protectedAuthEndpointNeedsAToken() throws Exception {
        assertThat(get("/api/v1/auth/me", Map.of()).statusCode()).isEqualTo(401);
        assertThat(HITS.get()).isZero();
    }

    @Test
    void actuatorHealthStaysPublic() throws Exception {
        assertThat(get("/actuator/health", Map.of()).statusCode()).isEqualTo(200);
    }
}
