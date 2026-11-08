package com.smartrecruit.application.controller;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /internal/v1/applications/counts?jobIds=. */
class InternalCountsIntegrationTest extends ApiTestSupport {

    private final UUID service = new UUID(0, 0);

    private void save(UUID jobId, ApplicationStatus status) {
        applications.save(Application.builder().id(UUID.randomUUID()).jobId(jobId).candidateId(UUID.randomUUID())
                .companyId(UUID.randomUUID()).status(status).appliedAt(Instant.now()).build());
    }

    @Test
    void countsApplicationsPerJobWithoutWithdrawnAndZeroFillsUnknownJobs() throws Exception {
        UUID busy = UUID.randomUUID();
        UUID quiet = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        UUID notRequested = UUID.randomUUID();
        save(busy, ApplicationStatus.RECEIVED);
        save(busy, ApplicationStatus.ACCEPTED);
        save(busy, ApplicationStatus.WITHDRAWN);
        save(quiet, ApplicationStatus.WITHDRAWN);
        save(notRequested, ApplicationStatus.RECEIVED);

        mockMvc.perform(as(get("/internal/v1/applications/counts").param("jobIds", busy + "," + quiet + "," + unknown),
                        service, "SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + busy + "']").value(2))
                .andExpect(jsonPath("$['" + quiet + "']").value(0))
                .andExpect(jsonPath("$['" + unknown + "']").value(0))
                .andExpect(jsonPath("$['" + notRequested + "']").doesNotExist());
    }

    @Test
    void emptyJobIdsGivesEmptyObject() throws Exception {
        mockMvc.perform(as(get("/internal/v1/applications/counts").param("jobIds", ""), service, "SERVICE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void missingJobIdsGets400() throws Exception {
        mockMvc.perform(as(get("/internal/v1/applications/counts"), service, "SERVICE")).andExpect(status().isBadRequest());
    }

    @Test
    void userRolesGet403() throws Exception {
        for (String role : new String[]{"CANDIDATE", "RECRUITER", "ADMIN"}) {
            mockMvc.perform(as(get("/internal/v1/applications/counts").param("jobIds", UUID.randomUUID().toString()),
                    UUID.randomUUID(), role)).andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/internal/v1/applications/counts").param("jobIds", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }
}
