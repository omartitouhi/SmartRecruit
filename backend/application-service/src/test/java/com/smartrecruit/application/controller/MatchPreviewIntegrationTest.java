package com.smartrecruit.application.controller;

import com.smartrecruit.application.support.ApiTestSupport;
import com.smartrecruit.application.support.FakeServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-MATCH-02 — GET /api/v1/applications/match-preview. */
class MatchPreviewIntegrationTest extends ApiTestSupport {

    private final UUID candidateId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final Instant parsedAt = Instant.parse("2026-01-02T10:00:00Z");
    private final Instant jobUpdatedAt = Instant.parse("2026-01-01T10:00:00Z");

    @BeforeEach
    void happyStubs() {
        stubJob(jobId, companyId, "PUBLISHED", jobUpdatedAt);
        stubReadiness(candidateId, true, true, parsedAt);
        stubScore(70);
    }

    private ResultActions preview(String role, UUID job) throws Exception {
        return mockMvc.perform(as(get("/api/v1/applications/match-preview").param("jobId", job.toString()), candidateId, role));
    }

    @Test
    void returnsTheMatchScore() throws Exception {
        preview("CANDIDATE", jobId).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(70.0))
                .andExpect(jsonPath("$.criteria[0].label").value("Skills"))
                .andExpect(jsonPath("$.criteria[0].weight").value(0.6))
                .andExpect(jsonPath("$.criteria[0].explanation").value("match"));
    }

    @Test
    void secondCallIsServedFromCacheWithoutCallingTheAi() throws Exception {
        preview("CANDIDATE", jobId).andExpect(status().isOk());
        stubScore(10);

        preview("CANDIDATE", jobId).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(70.0));

        assertThat(FakeServices.calls(AI_SCORE)).hasSize(1);
    }

    @Test
    void newCvAnalysisRecomputes() throws Exception {
        preview("CANDIDATE", jobId).andExpect(status().isOk());
        stubReadiness(candidateId, true, true, parsedAt.plusSeconds(3600));
        stubScore(20);

        preview("CANDIDATE", jobId).andExpect(jsonPath("$.score").value(20.0));

        assertThat(FakeServices.calls(AI_SCORE)).hasSize(2);
    }

    @Test
    void editedJobRecomputes() throws Exception {
        preview("CANDIDATE", jobId).andExpect(status().isOk());
        stubJob(jobId, companyId, "PUBLISHED", jobUpdatedAt.plusSeconds(3600));
        stubScore(30);

        preview("CANDIDATE", jobId).andExpect(jsonPath("$.score").value(30.0));

        assertThat(FakeServices.calls(AI_SCORE)).hasSize(2);
    }

    @Test
    void unknownJobGets404() throws Exception {
        preview("CANDIDATE", UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void candidateWithoutAnalysedCvGets422() throws Exception {
        stubReadiness(candidateId, true, true, null);
        preview("CANDIDATE", jobId).andExpect(status().isUnprocessableEntity());
        assertThat(FakeServices.calls(AI_SCORE)).isEmpty();
    }

    @Test
    void candidateWithoutProfileGets422() throws Exception {
        FakeServices.on("GET /internal/v1/candidates/" + candidateId + "/readiness", 404, "");
        preview("CANDIDATE", jobId).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aiFailureGets503AndIsNotCached() throws Exception {
        FakeServices.on(AI_SCORE, 500, "");
        preview("CANDIDATE", jobId).andExpect(status().isServiceUnavailable());

        stubScore(66);
        preview("CANDIDATE", jobId).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(66.0));
    }

    @Test
    void missingJobIdGets400() throws Exception {
        mockMvc.perform(as(get("/api/v1/applications/match-preview"), candidateId, "CANDIDATE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recruiterAndAdminGet403() throws Exception {
        preview("RECRUITER", jobId).andExpect(status().isForbidden());
        preview("ADMIN", jobId).andExpect(status().isForbidden());
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/v1/applications/match-preview").param("jobId", jobId.toString()))
                .andExpect(status().isUnauthorized());
    }
}
