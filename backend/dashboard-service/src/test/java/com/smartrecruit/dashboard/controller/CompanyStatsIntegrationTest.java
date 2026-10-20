package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.support.AbstractUpstreamIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-DASH-02 — GET /api/v1/dashboard/stats/company/{id}. */
class CompanyStatsIntegrationTest extends AbstractUpstreamIntegrationTest {

    private static final String URL = "/api/v1/dashboard/stats/company/{id}";

    private final UUID companyId = UUID.randomUUID();
    private final UUID recruiterId = UUID.randomUUID();

    private void membership(UUID memberOf) {
        FAKE.respond("GET", "/internal/v1/companies/by-member/" + recruiterId, 200,
                "{\"id\": \"%s\", \"name\": \"Acme\", \"logoUrl\": null}".formatted(memberOf));
    }

    private void sourcesUp() {
        FAKE.respond("GET", "/internal/v1/jobs/stats", 200,
                "{\"total\": 6, \"draft\": 1, \"published\": 3, \"closed\": 1, \"moderated\": 1}");
        FAKE.respond("GET", "/internal/v1/applications/stats", 200, """
                {"total": 20, "byStatus": {"PENDING": 14, "ACCEPTED": 3, "REJECTED": 3}, "averageMatchScore": 66.0,
                 "accepted": 3, "decided": 6, "acceptanceRate": 0.5}""");
    }

    @Test
    void aRecruiterSeesTheirOwnCompanyFigures() throws Exception {
        membership(companyId);
        sourcesUp();

        perform(get(URL, companyId), recruiterId, "RECRUITER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedJobs").value(3))
                .andExpect(jsonPath("$.applicationsReceived").value(20))
                .andExpect(jsonPath("$.acceptedApplications").value(3))
                .andExpect(jsonPath("$.acceptanceRate").value(0.5));

        assertThat(FAKE.callsTo("GET", "/internal/v1/jobs/stats").getFirst().target())
                .contains("companyId=" + companyId);
        assertThat(FAKE.callsTo("GET", "/internal/v1/applications/stats").getFirst().target())
                .contains("companyId=" + companyId);
    }

    @Test
    void acceptanceRateIsNullWhenNoDecisionWasTaken() throws Exception {
        membership(companyId);
        sourcesUp();
        FAKE.respond("GET", "/internal/v1/applications/stats", 200,
                "{\"total\": 2, \"byStatus\": {\"PENDING\": 2}, \"averageMatchScore\": null, \"accepted\": 0, \"decided\": 0, \"acceptanceRate\": null}");

        perform(get(URL, companyId), recruiterId, "RECRUITER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationsReceived").value(2))
                .andExpect(jsonPath("$.acceptanceRate").value(Matchers.nullValue()));
    }

    @Test
    void aRecruiterOfAnotherCompanyGets403AndNoFigureIsFetched() throws Exception {
        membership(UUID.randomUUID());
        sourcesUp();

        perform(get(URL, companyId), recruiterId, "RECRUITER").andExpect(status().isForbidden());

        assertThat(FAKE.callsTo("GET", "/internal/v1/jobs/stats")).isEmpty();
        assertThat(FAKE.callsTo("GET", "/internal/v1/applications/stats")).isEmpty();
    }

    @Test
    void aRecruiterWithoutCompanyGets403() throws Exception {
        sourcesUp();

        perform(get(URL, companyId), recruiterId, "RECRUITER").andExpect(status().isForbidden());
    }

    @Test
    void membershipCannotBeVerifiedWhileUserServiceIsDownGives502() throws Exception {
        FAKE.respond("GET", "/internal/v1/companies/by-member/" + recruiterId, 500, "boom");
        sourcesUp();

        perform(get(URL, companyId), recruiterId, "RECRUITER").andExpect(status().isBadGateway());
    }

    @Test
    void anAdminSeesAnyCompanyWithoutMembershipCheck() throws Exception {
        sourcesUp();

        asAdmin(get(URL, companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedJobs").value(3));

        assertThat(FAKE.calls()).noneMatch(call -> call.target().contains("by-member"));
    }

    @Test
    void aSourceDownOnlyNullsItsOwnValues() throws Exception {
        membership(companyId);
        sourcesUp();
        FAKE.respond("GET", "/internal/v1/applications/stats", 500, "boom");

        perform(get(URL, companyId), recruiterId, "RECRUITER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedJobs").value(3))
                .andExpect(jsonPath("$.applicationsReceived").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.acceptedApplications").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.acceptanceRate").value(Matchers.nullValue()));
    }

    @Test
    void candidatesAndAnonymousCallersAreDenied() throws Exception {
        perform(get(URL, companyId), UUID.randomUUID(), "CANDIDATE").andExpect(status().isForbidden());
        mockMvc.perform(get(URL, companyId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/dashboard/stats/company/not-a-uuid")).andExpect(status().isUnauthorized());
        perform(get("/api/v1/dashboard/stats/company/not-a-uuid"), recruiterId, "RECRUITER")
                .andExpect(status().isBadRequest());
        assertThat(FAKE.calls()).isEmpty();
    }
}
