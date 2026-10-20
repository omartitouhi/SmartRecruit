package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.support.AbstractUpstreamIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-DASH-01 — GET /api/v1/dashboard/stats/global aggregated from auth, job and application services. */
class GlobalStatsIntegrationTest extends AbstractUpstreamIntegrationTest {

    private static final String URL = "/api/v1/dashboard/stats/global";

    private void allSourcesUp() {
        FAKE.respond("GET", "/internal/v1/accounts/stats", 200, """
                {"total": 10, "byRole": {"CANDIDATE": 7, "RECRUITER": 2, "ADMIN": 1},
                 "byStatus": {"ACTIVE": 9, "DISABLED": 1, "DELETED": 0}}""");
        FAKE.respond("GET", "/internal/v1/jobs/stats", 200,
                "{\"total\": 8, \"draft\": 1, \"published\": 5, \"closed\": 1, \"moderated\": 1}");
        FAKE.respond("GET", "/internal/v1/applications/stats", 200, """
                {"total": 42, "byStatus": {"PENDING": 40, "ACCEPTED": 2}, "averageMatchScore": 71.5,
                 "accepted": 2, "decided": 4, "acceptanceRate": 0.5}""");
    }

    @Test
    void aggregatesTheThreeSources() throws Exception {
        allSourcesUp();

        asAdmin(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeJobs").value(5))
                .andExpect(jsonPath("$.totalApplications").value(42))
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.usersByRole.CANDIDATE").value(7))
                .andExpect(jsonPath("$.usersByRole.RECRUITER").value(2))
                .andExpect(jsonPath("$.usersByRole.ADMIN").value(1))
                .andExpect(jsonPath("$.averageMatchScore").value(71.5));

        assertThat(FAKE.callsTo("GET", "/internal/v1/jobs/stats").getFirst().target()).doesNotContain("companyId");
        assertThat(FAKE.calls()).allSatisfy(call -> {
            assertThat(call.role()).isEqualTo("SERVICE");
            assertThat(call.h2cUpgrade()).isFalse();
        });
    }

    @Test
    void averageMatchScoreStaysNullWhenThereIsNothingToAverage() throws Exception {
        allSourcesUp();
        FAKE.respond("GET", "/internal/v1/applications/stats", 200,
                "{\"total\": 0, \"byStatus\": {}, \"averageMatchScore\": null, \"accepted\": 0, \"decided\": 0, \"acceptanceRate\": null}");

        asAdmin(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.averageMatchScore").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void aSourceReturningAnErrorOnlyNullsItsOwnValues() throws Exception {
        allSourcesUp();
        FAKE.respond("GET", "/internal/v1/jobs/stats", 500, "boom");

        asAdmin(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeJobs").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.totalApplications").value(42))
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.averageMatchScore").value(71.5));
    }

    @Test
    void authServiceDownNullsUserFigures() throws Exception {
        allSourcesUp();
        FAKE.respond("GET", "/internal/v1/accounts/stats", 503, "");

        asAdmin(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.usersByRole").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.activeJobs").value(5));
    }

    @Test
    void everySourceDownStillAnswers200WithNulls() throws Exception {
        asAdmin(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeJobs").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.totalApplications").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.totalUsers").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.averageMatchScore").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void isReservedToAdministrators() throws Exception {
        for (String role : new String[]{"RECRUITER", "CANDIDATE"}) {
            perform(get(URL), UUID.randomUUID(), role).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        assertThat(FAKE.calls()).isEmpty();
    }
}
