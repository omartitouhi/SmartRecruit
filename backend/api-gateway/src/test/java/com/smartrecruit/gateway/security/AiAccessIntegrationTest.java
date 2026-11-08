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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authorization of {@code /api/v1/ai/**} at the gateway: deny by default, candidates only reach their own data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiAccessIntegrationTest {

    private static final HttpServer DOWNSTREAM;
    private static final AtomicInteger HITS = new AtomicInteger();
    private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();
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

    private final HttpClient client = HttpClient.newHttpClient();

    private final UUID candidate = UUID.randomUUID();
    private final UUID otherCandidate = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void reset() {
        HITS.set(0);
        LAST_BODY.set(null);
        LAST_HEADERS.clear();
    }

    private String bearer(UUID userId, String role) {
        return "Bearer " + Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private HttpResponse<String> call(String method, String path, String authorization, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String skillGap(UUID candidateId) {
        return "/api/v1/ai/skill-gap/candidates/" + candidateId + "/jobs/" + jobId;
    }

    private String score(UUID cvId) {
        return "{\"cvId\": \"" + cvId + "\", \"jobId\": \"" + jobId + "\"}";
    }

    @Test
    void candidateReadsOwnSkillGap() throws Exception {
        HttpResponse<String> response = call("GET", skillGap(candidate), bearer(candidate, "CANDIDATE"), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(HITS.get()).isEqualTo(1);
    }

    @Test
    void candidateCannotReadAnotherCandidatesData() throws Exception {
        String token = bearer(candidate, "CANDIDATE");
        List<String> paths = List.of(
                skillGap(otherCandidate),
                "/api/v1/ai/recommendation/candidates/" + otherCandidate + "/jobs",
                "/api/v1/ai/cv-parsing/candidates/" + otherCandidate);

        for (String path : paths) {
            HttpResponse<String> response = call("GET", path, token, null);
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.body()).contains("appartiennent à un autre candidat");
        }
        assertThat(HITS.get()).isZero();
    }

    @Test
    void candidateReadsOwnRecommendationsAndParsedCvIgnoringUuidCase() throws Exception {
        String token = bearer(candidate, "CANDIDATE");

        assertThat(call("GET", "/api/v1/ai/recommendation/candidates/" + candidate + "/jobs", token, null)
                .statusCode()).isEqualTo(200);
        assertThat(call("GET", "/api/v1/ai/cv-parsing/candidates/" + candidate.toString().toUpperCase(), token, null)
                .statusCode()).isEqualTo(200);
    }

    @Test
    void recruiterReadsAnyCandidatesSkillGap() throws Exception {
        HttpResponse<String> response = call("GET", skillGap(otherCandidate), bearer(UUID.randomUUID(), "RECRUITER"), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(HITS.get()).isEqualTo(1);
    }

    @Test
    void candidateScoresOwnCvAndDownstreamReceivesTheOriginalBodyAndIdentity() throws Exception {
        String body = score(candidate);

        HttpResponse<String> response = call("POST", "/api/v1/ai/matching/score", bearer(candidate, "CANDIDATE"), body);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(LAST_BODY.get()).isEqualTo(body);
        assertThat(LAST_HEADERS.get("x-auth-user-id")).isEqualTo(candidate.toString());
        assertThat(LAST_HEADERS.get("x-auth-user-role")).isEqualTo("CANDIDATE");
        assertThat(LAST_HEADERS).containsKeys("x-auth-timestamp", "x-auth-signature");
    }

    @Test
    void recruiterScoresAnyCv() throws Exception {
        String body = score(otherCandidate);

        HttpResponse<String> response = call("POST", "/api/v1/ai/matching/score", bearer(UUID.randomUUID(), "RECRUITER"), body);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(LAST_BODY.get()).isEqualTo(body);
    }

    @Test
    void candidateCannotScoreAnotherCandidatesCv() throws Exception {
        HttpResponse<String> response = call("POST", "/api/v1/ai/matching/score", bearer(candidate, "CANDIDATE"),
                score(otherCandidate));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(HITS.get()).isZero();
    }

    @Test
    void candidateScoreWithInvalidOrMissingBodyIsBadRequest() throws Exception {
        String token = bearer(candidate, "CANDIDATE");
        List<String> bodies = List.of("not json", "{}", "{\"jobId\": \"" + jobId + "\"}", "{\"cvId\": \"nope\"}",
                "{\"cvId\": 42}", "[]");

        for (String body : bodies) {
            HttpResponse<String> response = call("POST", "/api/v1/ai/matching/score", token, body);
            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat(response.body()).contains("cvId manquant");
        }
        assertThat(call("POST", "/api/v1/ai/matching/score", token, null).statusCode()).isEqualTo(400);
        assertThat(HITS.get()).isZero();
    }

    @Test
    void oversizedScoreBodyIsBadRequest() throws Exception {
        String body = "{\"cvId\": \"" + candidate + "\", \"pad\": \"" + "x".repeat(5000) + "\"}";

        HttpResponse<String> response = call("POST", "/api/v1/ai/matching/score", bearer(candidate, "CANDIDATE"), body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("trop volumineux");
        assertThat(HITS.get()).isZero();
    }

    @Test
    void internalEndpointsAreForbiddenEvenForAdmin() throws Exception {
        List<String> tokens = List.of(bearer(candidate, "CANDIDATE"), bearer(UUID.randomUUID(), "ADMIN"));

        for (String token : tokens) {
            for (String[] request : new String[][]{
                    {"POST", "/api/v1/ai/cv-parsing/parse"},
                    {"POST", "/api/v1/ai/job-analysis/analyze"},
                    {"DELETE", "/api/v1/ai/candidates/" + candidate + "/data"},
                    {"DELETE", skillGap(candidate)},
                    {"GET", "/api/v1/ai/ping"}}) {
                HttpResponse<String> response = call(request[0], request[1], token,
                        "POST".equals(request[0]) ? "{}" : null);
                assertThat(response.statusCode()).as(request[0] + " " + request[1]).isEqualTo(403);
                assertThat(response.body()).contains("réservé aux services internes");
            }
        }
        assertThat(HITS.get()).isZero();
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        assertThat(call("GET", skillGap(candidate), null, null).statusCode()).isEqualTo(401);
        assertThat(call("POST", "/api/v1/ai/matching/score", null, score(candidate)).statusCode()).isEqualTo(401);
        assertThat(HITS.get()).isZero();
    }
}
