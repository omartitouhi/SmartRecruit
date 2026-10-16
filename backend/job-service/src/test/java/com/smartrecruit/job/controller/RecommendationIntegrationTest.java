package com.smartrecruit.job.controller;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.repository.JobRepository;
import com.smartrecruit.job.security.InternalIdentityVerifier;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-REC-01 — GET /api/v1/jobs/recommendations. A JDK HTTP server plays ai-service and user-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecommendationIntegrationTest {

    record Response(int status, String body) {
    }

    private static final HttpServer SERVICES;
    private static final Map<String, Response> RESPONSES = new ConcurrentHashMap<>();
    private static final List<String> CALLS = new CopyOnWriteArrayList<>();
    private static final Map<String, String> LAST_ROLE_BY_PREFIX = new ConcurrentHashMap<>();

    static {
        try {
            SERVICES = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SERVICES.createContext("/", RecommendationIntegrationTest::handle);
        SERVICES.start();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        CALLS.add(path + (query == null ? "" : "?" + query));
        String prefix = path.startsWith("/internal/") ? "user" : "ai";
        LAST_ROLE_BY_PREFIX.put(prefix, String.valueOf(exchange.getRequestHeaders().getFirst("X-Auth-User-Role")));
        Response response = RESPONSES.getOrDefault(prefix, new Response(404, ""));
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    @DynamicPropertySource
    static void pointToFakeServices(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + SERVICES.getAddress().getPort();
        registry.add("services.ai-service-uri", () -> uri);
        registry.add("services.user-service-uri", () -> uri);
    }

    @AfterAll
    static void stop() {
        SERVICES.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private JobRepository jobRepository;

    private final UUID candidateId = UUID.randomUUID();
    private final UUID acme = UUID.randomUUID();
    private final UUID globex = UUID.randomUUID();
    private UUID javaJob;
    private UUID dataJob;
    private UUID closedJob;
    private UUID draftJob;

    @BeforeEach
    void setUp() {
        RESPONSES.clear();
        CALLS.clear();
        LAST_ROLE_BY_PREFIX.clear();
        jobRepository.deleteAll();
        javaJob = saveJob("Développeur Java", acme, JobStatus.PUBLISHED);
        dataJob = saveJob("Data Engineer", globex, JobStatus.PUBLISHED);
        closedJob = saveJob("Offre clôturée", acme, JobStatus.CLOSED);
        draftJob = saveJob("Brouillon", acme, JobStatus.DRAFT);
        RESPONSES.put("user", new Response(200, """
                [{"id": "%s", "name": "Acme", "logoUrl": null}, {"id": "%s", "name": "Globex", "logoUrl": null}]
                """.formatted(acme, globex)));
    }

    private UUID saveJob(String title, UUID companyId, JobStatus status) {
        UUID id = UUID.randomUUID();
        jobRepository.save(Job.builder()
                .id(id).companyId(companyId).title(title).description("Description")
                .requiredSkills(new java.util.ArrayList<>(List.of("Java")))
                .experienceLevel(ExperienceLevel.SENIOR).contractType(ContractType.CDI)
                .location("Tunis").status(status)
                .publishedAt(status == JobStatus.PUBLISHED ? Instant.parse("2026-10-01T09:00:00Z") : null)
                .build());
        return id;
    }

    private void aiReturns(String recommendationsJson) {
        RESPONSES.put("ai", new Response(200, "{\"recommendations\": " + recommendationsJson + "}"));
    }

    private ResultActions recommendations(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/jobs/recommendations" + query)
                .with(request -> {
                    long now = Instant.now().getEpochSecond();
                    request.addHeader(InternalIdentityVerifier.USER_ID, candidateId.toString());
                    request.addHeader(InternalIdentityVerifier.USER_ROLE, "CANDIDATE");
                    request.addHeader(InternalIdentityVerifier.TIMESTAMP, Long.toString(now));
                    request.addHeader(InternalIdentityVerifier.SIGNATURE, verifier.sign(candidateId.toString(), "CANDIDATE", now));
                    return request;
                }));
    }

    @Test
    void aiScoresAreEnrichedWithOfferAndCompanyAndSortedByScore() throws Exception {
        aiReturns("""
                [{"jobId": "%s", "score": 72.5}, {"jobId": "%s", "score": 91.0}]
                """.formatted(javaJob, dataJob));

        recommendations("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].jobId").value(dataJob.toString()))
                .andExpect(jsonPath("$[0].title").value("Data Engineer"))
                .andExpect(jsonPath("$[0].companyName").value("Globex"))
                .andExpect(jsonPath("$[0].score").value(91.0))
                .andExpect(jsonPath("$[1].title").value("Développeur Java"))
                .andExpect(jsonPath("$[1].companyId").value(acme.toString()))
                .andExpect(jsonPath("$[1].companyName").value("Acme"))
                .andExpect(jsonPath("$[1].location").value("Tunis"))
                .andExpect(jsonPath("$[1].contractType").value("CDI"))
                .andExpect(jsonPath("$[1].experienceLevel").value("SENIOR"))
                .andExpect(jsonPath("$[1].publishedAt").value("2026-10-01T09:00:00Z"))
                .andExpect(jsonPath("$[1].score").value(72.5))
                .andExpect(jsonPath("$[1].description").doesNotExist());

        assertThat(CALLS).contains("/api/v1/ai/recommendation/candidates/" + candidateId + "/jobs");
        assertThat(LAST_ROLE_BY_PREFIX).containsEntry("ai", "SERVICE").containsEntry("user", "SERVICE");
    }

    @Test
    void unpublishedUnknownAndDuplicatedOffersAreFiltered() throws Exception {
        aiReturns("""
                [{"jobId": "%s", "score": 99}, {"jobId": "%s", "score": 98}, {"jobId": "%s", "score": 97},
                 {"jobId": "%s", "score": 60}, {"jobId": "%s", "score": 80}]
                """.formatted(closedJob, draftJob, UUID.randomUUID(), javaJob, javaJob));

        recommendations("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobId").value(javaJob.toString()))
                .andExpect(jsonPath("$[0].score").value(80.0));
    }

    @Test
    void limitKeepsTheBestOffers() throws Exception {
        aiReturns("""
                [{"jobId": "%s", "score": 50}, {"jobId": "%s", "score": 70}]
                """.formatted(javaJob, dataJob));

        recommendations("?limit=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobId").value(dataJob.toString()));
        recommendations("?limit=0").andExpect(status().isBadRequest());
        recommendations("?limit=51").andExpect(status().isBadRequest());
    }

    @Test
    void emptyRecommendationsGiveAnEmptyList() throws Exception {
        aiReturns("[]");

        recommendations("").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(CALLS).noneMatch(call -> call.startsWith("/internal/"));
    }

    @Test
    void aiServiceFailureReturns503() throws Exception {
        RESPONSES.put("ai", new Response(501, "{\"detail\": \"Recommendation not implemented yet\"}"));

        recommendations("")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Les recommandations sont temporairement indisponibles, réessayez plus tard"));
    }

    @Test
    void userServiceFailureStillReturnsTheListWithoutCompanyNames() throws Exception {
        aiReturns("""
                [{"jobId": "%s", "score": 72.5}]
                """.formatted(javaJob));
        RESPONSES.put("user", new Response(500, ""));

        recommendations("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Développeur Java"))
                .andExpect(jsonPath("$[0].companyId").value(acme.toString()))
                .andExpect(jsonPath("$[0].companyName").doesNotExist());
    }

    @Test
    void onlyCandidatesGetRecommendations() throws Exception {
        UUID recruiter = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        mockMvc.perform(get("/api/v1/jobs/recommendations")
                        .header(InternalIdentityVerifier.USER_ID, recruiter.toString())
                        .header(InternalIdentityVerifier.USER_ROLE, "RECRUITER")
                        .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(now))
                        .header(InternalIdentityVerifier.SIGNATURE, verifier.sign(recruiter.toString(), "RECRUITER", now)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/jobs/recommendations")).andExpect(status().isUnauthorized());
    }
}
