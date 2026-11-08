package com.smartrecruit.application.support;

import com.smartrecruit.application.repository.ApplicationNoteRepository;
import com.smartrecruit.application.repository.ApplicationRepository;
import com.smartrecruit.application.repository.MatchScoreCacheRepository;
import com.smartrecruit.application.security.InternalIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Base of the endpoint tests: MockMvc with signed gateway headers and the downstream services faked
 * by {@link FakeServices}.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    @DynamicPropertySource
    static void pointToFakeServices(DynamicPropertyRegistry registry) {
        registry.add("services.job-service-uri", FakeServices::uri);
        registry.add("services.user-service-uri", FakeServices::uri);
        registry.add("services.ai-service-uri", FakeServices::uri);
        registry.add("services.dashboard-service-uri", FakeServices::uri);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected InternalIdentityVerifier verifier;

    @Autowired
    protected ApplicationRepository applications;

    @Autowired
    protected MatchScoreCacheRepository scoreCache;

    @Autowired
    protected ApplicationNoteRepository notes;

    @BeforeEach
    void resetState() {
        FakeServices.reset();
        notes.deleteAll();
        applications.deleteAll();
        scoreCache.deleteAll();
    }

    protected MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID userId, String role) {
        long now = Instant.now().getEpochSecond();
        return request
                .header(InternalIdentityVerifier.USER_ID, userId.toString())
                .header(InternalIdentityVerifier.USER_ROLE, role)
                .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(now))
                .header(InternalIdentityVerifier.SIGNATURE, verifier.sign(userId.toString(), role, now));
    }

    protected static void stubJob(UUID jobId, UUID companyId, String status, Instant updatedAt) {
        FakeServices.on("GET /internal/v1/jobs/" + jobId, 200,
                "{\"id\":\"%s\",\"companyId\":\"%s\",\"title\":\"Dev Java\",\"status\":\"%s\",\"updatedAt\":\"%s\"}"
                        .formatted(jobId, companyId, status, updatedAt));
    }

    /** Same as {@link #stubJob(UUID, UUID, String, Instant)} with a last day to apply (EXT-JOB-01). */
    protected static void stubJob(UUID jobId, UUID companyId, String status, Instant updatedAt, java.time.LocalDate deadline) {
        FakeServices.on("GET /internal/v1/jobs/" + jobId, 200,
                ("{\"id\":\"%s\",\"companyId\":\"%s\",\"title\":\"Dev Java\",\"status\":\"%s\",\"updatedAt\":\"%s\","
                        + "\"applicationDeadline\":%s}").formatted(jobId, companyId, status, updatedAt,
                        deadline == null ? "null" : "\"" + deadline + "\""));
    }

    protected static void stubReadiness(UUID candidateId, boolean profileComplete, boolean hasCv, Instant cvParsedAt) {
        FakeServices.on("GET /internal/v1/candidates/" + candidateId + "/readiness", 200,
                "{\"profileComplete\":%s,\"hasCv\":%s,\"cvAnalysisStatus\":\"%s\",\"cvParsedAt\":%s}".formatted(
                        profileComplete, hasCv, cvParsedAt == null ? "NONE" : "DONE",
                        cvParsedAt == null ? "null" : "\"" + cvParsedAt + "\""));
    }

    protected static void stubCompanyOf(UUID recruiterId, UUID companyId) {
        FakeServices.on("GET /internal/v1/companies/by-member/" + recruiterId, 200,
                "{\"id\":\"%s\",\"name\":\"Acme\",\"logoUrl\":null}".formatted(companyId));
    }

    protected static final String AI_SCORE = "POST /api/v1/ai/matching/score";

    protected static void stubScore(double score) {
        FakeServices.on(AI_SCORE, 200, ("{\"score\":%s,\"criteria\":[{\"label\":\"Skills\",\"weight\":0.6,"
                + "\"contribution\":%s,\"explanation\":\"match\"}]}").formatted(score, score * 0.6));
    }
}
