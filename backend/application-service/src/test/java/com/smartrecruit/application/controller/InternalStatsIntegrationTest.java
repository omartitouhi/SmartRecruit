package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /internal/v1/applications/stats?companyId=. */
class InternalStatsIntegrationTest extends ApiTestSupport {

    private final UUID service = new UUID(0, 0);
    private final UUID acme = UUID.randomUUID();
    private final UUID globex = UUID.randomUUID();

    private void save(UUID company, ApplicationStatus status, Double score) {
        applications.save(Application.builder().id(UUID.randomUUID()).jobId(UUID.randomUUID()).candidateId(UUID.randomUUID())
                .companyId(company).status(status).appliedAt(Instant.now())
                .matchScore(score == null ? null : new MatchScoreDto(score, List.of())).build());
    }

    private ResultActions stats(String role, UUID companyId) throws Exception {
        var request = get("/internal/v1/applications/stats");
        if (companyId != null) {
            request.param("companyId", companyId.toString());
        }
        return mockMvc.perform(as(request, service, role));
    }

    private void seed() {
        save(acme, ApplicationStatus.RECEIVED, 50.0);
        save(acme, ApplicationStatus.IN_REVIEW, null);
        save(acme, ApplicationStatus.ACCEPTED, 90.0);
        save(acme, ApplicationStatus.ACCEPTED, null);
        save(acme, ApplicationStatus.REJECTED, 10.0);
        save(acme, ApplicationStatus.WITHDRAWN, 100.0);
        save(globex, ApplicationStatus.INTERVIEW, 30.0);
        save(globex, ApplicationStatus.REJECTED, null);
    }

    @Test
    void companyStatsCountOnlyItsOwnNonWithdrawnApplications() throws Exception {
        seed();

        stats("SERVICE", acme).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.byStatus.RECEIVED").value(1))
                .andExpect(jsonPath("$.byStatus.IN_REVIEW").value(1))
                .andExpect(jsonPath("$.byStatus.INTERVIEW").value(0))
                .andExpect(jsonPath("$.byStatus.ACCEPTED").value(2))
                .andExpect(jsonPath("$.byStatus.REJECTED").value(1))
                .andExpect(jsonPath("$.byStatus.WITHDRAWN").doesNotExist())
                .andExpect(jsonPath("$.averageMatchScore").value(50.0))
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.decided").value(3))
                .andExpect(jsonPath("$.acceptanceRate").value(2.0 / 3));
    }

    @Test
    void withoutCompanyAggregatesEverything() throws Exception {
        seed();

        stats("SERVICE", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.byStatus.INTERVIEW").value(1))
                .andExpect(jsonPath("$.byStatus.REJECTED").value(2))
                .andExpect(jsonPath("$.averageMatchScore").value(45.0))
                .andExpect(jsonPath("$.decided").value(4))
                .andExpect(jsonPath("$.acceptanceRate").value(0.5));
    }

    @Test
    void emptyCompanyHasNullAverageAndRate() throws Exception {
        seed();

        stats("SERVICE", UUID.randomUUID()).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.byStatus.RECEIVED").value(0))
                .andExpect(jsonPath("$.averageMatchScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.decided").value(0))
                .andExpect(jsonPath("$.acceptanceRate").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void nothingDecidedMeansNullRate() throws Exception {
        save(acme, ApplicationStatus.RECEIVED, null);

        stats("SERVICE", acme).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.averageMatchScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.acceptanceRate").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void userRolesGet403() throws Exception {
        for (String role : new String[]{"CANDIDATE", "RECRUITER", "ADMIN"}) {
            stats(role, acme).andExpect(status().isForbidden());
        }
    }

    @Test
    void invalidCompanyIdGets400() throws Exception {
        mockMvc.perform(as(get("/internal/v1/applications/stats").param("companyId", "nope"), service, "SERVICE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/internal/v1/applications/stats")).andExpect(status().isUnauthorized());
    }
}
