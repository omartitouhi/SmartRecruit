package com.smartrecruit.application.controller;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.support.ApiTestSupport;
import com.smartrecruit.application.support.FakeServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-APP-01 — POST /api/v1/applications. */
class ApplyIntegrationTest extends ApiTestSupport {

    private final UUID candidateId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void happyStubs() {
        stubJob(jobId, companyId, "PUBLISHED", Instant.parse("2026-01-01T10:00:00Z"));
        stubReadiness(candidateId, true, true, Instant.parse("2026-01-02T10:00:00Z"));
    }

    private ResultActions apply(UUID caller, String role, String body) throws Exception {
        return mockMvc.perform(as(post("/api/v1/applications"), caller, role)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions apply(String role, String body) throws Exception {
        return apply(candidateId, role, body);
    }

    private ResultActions apply() throws Exception {
        return apply("CANDIDATE", "{\"jobId\":\"%s\"}".formatted(jobId));
    }

    @Test
    void createsReceivedApplicationWithInitialHistoryAndCompany() throws Exception {
        apply().andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.candidateId").value(candidateId.toString()))
                .andExpect(jsonPath("$.matchScore").doesNotExist())
                .andExpect(jsonPath("$.appliedAt").exists())
                .andExpect(jsonPath("$.statusHistory.length()").value(1))
                .andExpect(jsonPath("$.statusHistory[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$.statusHistory[0].changedBy").value(candidateId.toString()));

        Application stored = applications.findAll().getFirst();
        assertThat(stored.getCompanyId()).isEqualTo(companyId);
    }

    @Test
    void unknownJobGets404() throws Exception {
        apply("CANDIDATE", "{\"jobId\":\"%s\"}".formatted(UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void unpublishedJobGets409() throws Exception {
        for (String jobStatus : new String[]{"DRAFT", "CLOSED", "MODERATED"}) {
            stubJob(jobId, companyId, jobStatus, Instant.now());
            apply().andExpect(status().isConflict());
        }
        assertThat(applications.count()).isZero();
    }

    @Test
    void incompleteProfileGets422() throws Exception {
        stubReadiness(candidateId, false, true, Instant.now());
        apply().andExpect(status().isUnprocessableEntity());
    }

    @Test
    void missingCvGets422() throws Exception {
        stubReadiness(candidateId, true, false, null);
        apply().andExpect(status().isUnprocessableEntity());
    }

    @Test
    void candidateWithoutProfileGets422() throws Exception {
        FakeServices.on("GET /internal/v1/candidates/" + candidateId + "/readiness", 404, "");
        apply().andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cvStillBeingAnalysedDoesNotBlockApplying() throws Exception {
        stubReadiness(candidateId, true, true, null);
        apply().andExpect(status().isCreated());
    }

    @Test
    void secondApplicationToSameJobGets409() throws Exception {
        apply().andExpect(status().isCreated());
        apply().andExpect(status().isConflict());
        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    void canApplyAgainAfterWithdrawing() throws Exception {
        apply().andExpect(status().isCreated());
        Application first = applications.findAll().getFirst();
        first.setStatus(ApplicationStatus.WITHDRAWN);
        applications.save(first);

        apply().andExpect(status().isCreated());
        assertThat(applications.count()).isEqualTo(2);
    }

    @Test
    void sameJobFromAnotherCandidateIsNotADuplicate() throws Exception {
        apply().andExpect(status().isCreated());
        UUID other = UUID.randomUUID();
        stubReadiness(other, true, true, Instant.now());
        apply(other, "CANDIDATE", "{\"jobId\":\"%s\"}".formatted(jobId)).andExpect(status().isCreated());
    }

    @Test
    void missingJobIdGets400WithFieldErrors() throws Exception {
        apply("CANDIDATE", "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.jobId").exists());
    }

    @Test
    void malformedJobIdGets400() throws Exception {
        apply("CANDIDATE", "{\"jobId\":\"not-a-uuid\"}").andExpect(status().isBadRequest());
    }

    @Test
    void recruiterAndAdminGet403() throws Exception {
        apply("RECRUITER", "{\"jobId\":\"%s\"}".formatted(jobId)).andExpect(status().isForbidden());
        apply("ADMIN", "{\"jobId\":\"%s\"}".formatted(jobId)).andExpect(status().isForbidden());
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\":\"%s\"}".formatted(jobId))).andExpect(status().isUnauthorized());
    }

    @Test
    void jobServiceDownGets503() throws Exception {
        FakeServices.on("GET /internal/v1/jobs/" + jobId, 500, "");
        apply().andExpect(status().isServiceUnavailable());
    }

    @Test
    void downstreamCallsAreSignedAsServiceOverHttp11() throws Exception {
        apply().andExpect(status().isCreated());
        var call = FakeServices.calls("GET /internal/v1/jobs/" + jobId).getFirst();
        assertThat(call.role()).isEqualTo("SERVICE");
        assertThat(call.h2cUpgrade()).isFalse();
    }
}
