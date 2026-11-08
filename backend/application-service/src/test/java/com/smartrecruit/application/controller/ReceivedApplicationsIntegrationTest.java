package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.support.ApiTestSupport;
import com.smartrecruit.application.support.FakeServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-APP-03 — GET /api/v1/applications?jobId=. */
class ReceivedApplicationsIntegrationTest extends ApiTestSupport {

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void ownJob() {
        stubJob(jobId, companyId, "PUBLISHED", Instant.now());
        stubCompanyOf(recruiterId, companyId);
    }

    private Application save(UUID job, ApplicationStatus status, Double score, String appliedAt) {
        return applications.save(Application.builder().id(UUID.randomUUID()).jobId(job).candidateId(UUID.randomUUID())
                .companyId(companyId).status(status).appliedAt(Instant.parse(appliedAt))
                .matchScore(score == null ? null : new MatchScoreDto(score, List.of())).build());
    }

    private ResultActions list(UUID caller, String role, UUID job) throws Exception {
        return mockMvc.perform(as(get("/api/v1/applications").param("jobId", job.toString()), caller, role));
    }

    @Test
    void ownerRecruiterSeesApplicationsBestScoreFirstUnscoredLast() throws Exception {
        Application low = save(jobId, ApplicationStatus.RECEIVED, 40.0, "2026-01-01T00:00:00Z");
        Application none = save(jobId, ApplicationStatus.RECEIVED, null, "2026-01-02T00:00:00Z");
        Application high = save(jobId, ApplicationStatus.IN_REVIEW, 90.5, "2026-01-03T00:00:00Z");
        Application zero = save(jobId, ApplicationStatus.RECEIVED, 0.0, "2026-01-04T00:00:00Z");
        Application noneLater = save(jobId, ApplicationStatus.REJECTED, null, "2026-01-05T00:00:00Z");

        list(recruiterId, "RECRUITER", jobId).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value(high.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(low.getId().toString()))
                .andExpect(jsonPath("$[2].id").value(zero.getId().toString()))
                .andExpect(jsonPath("$[3].id").value(none.getId().toString()))
                .andExpect(jsonPath("$[4].id").value(noneLater.getId().toString()));
    }

    @Test
    void withdrawnApplicationsAreExcludedAndOtherJobsIgnored() throws Exception {
        Application active = save(jobId, ApplicationStatus.RECEIVED, 10.0, "2026-01-01T00:00:00Z");
        save(jobId, ApplicationStatus.WITHDRAWN, 99.0, "2026-01-02T00:00:00Z");
        save(UUID.randomUUID(), ApplicationStatus.RECEIVED, 99.0, "2026-01-03T00:00:00Z");

        list(recruiterId, "RECRUITER", jobId).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(active.getId().toString()));
    }

    @Test
    void recruiterOfAnotherCompanyGets403() throws Exception {
        UUID stranger = UUID.randomUUID();
        stubCompanyOf(stranger, UUID.randomUUID());
        list(stranger, "RECRUITER", jobId).andExpect(status().isForbidden());
    }

    @Test
    void recruiterWithoutCompanyGets403() throws Exception {
        UUID stranger = UUID.randomUUID();
        FakeServices.on("GET /internal/v1/companies/by-member/" + stranger, 404, "");
        list(stranger, "RECRUITER", jobId).andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAnyJob() throws Exception {
        Application application = save(jobId, ApplicationStatus.RECEIVED, 10.0, "2026-01-01T00:00:00Z");

        list(UUID.randomUUID(), "ADMIN", jobId).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(application.getId().toString()));
    }

    @Test
    void unknownJobGets404() throws Exception {
        list(recruiterId, "RECRUITER", UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void candidateGets403() throws Exception {
        list(recruiterId, "CANDIDATE", jobId).andExpect(status().isForbidden());
    }

    @Test
    void missingJobIdGets400() throws Exception {
        mockMvc.perform(as(get("/api/v1/applications"), recruiterId, "RECRUITER")).andExpect(status().isBadRequest());
    }

    @Test
    void userServiceDownGets503() throws Exception {
        FakeServices.on("GET /internal/v1/companies/by-member/" + recruiterId, 500, "");
        list(recruiterId, "RECRUITER", jobId).andExpect(status().isServiceUnavailable());
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/v1/applications").param("jobId", jobId.toString())).andExpect(status().isUnauthorized());
    }
}
