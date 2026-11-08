package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.Interview;
import com.smartrecruit.application.model.InterviewMode;
import com.smartrecruit.application.model.StatusHistoryEntry;
import com.smartrecruit.application.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
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

    /** EXT-DASH-01: applied → first decision, in days. */
    private void saveDecided(UUID company, ApplicationStatus decision, Instant appliedAt, Duration delay) {
        Application application = Application.builder().id(UUID.randomUUID()).jobId(UUID.randomUUID())
                .candidateId(UUID.randomUUID()).companyId(company).status(decision).appliedAt(appliedAt).build();
        application.getStatusHistory().add(new StatusHistoryEntry(ApplicationStatus.RECEIVED, appliedAt, application.getCandidateId()));
        application.getStatusHistory().add(new StatusHistoryEntry(ApplicationStatus.IN_REVIEW, appliedAt.plus(Duration.ofDays(1)), service));
        application.getStatusHistory().add(new StatusHistoryEntry(decision, appliedAt.plus(delay), service));
        applications.save(application);
    }

    private void saveInterview(UUID company, Instant at) {
        applications.save(Application.builder().id(UUID.randomUUID()).jobId(UUID.randomUUID()).candidateId(UUID.randomUUID())
                .companyId(company).status(ApplicationStatus.INTERVIEW).appliedAt(Instant.now())
                .interview(Interview.builder().scheduledAt(at).durationMinutes(60).mode(InterviewMode.PHONE).build()).build());
    }

    @Test
    void averageDecisionDaysAndUpcomingInterviews() throws Exception {
        Instant applied = Instant.parse("2026-03-01T08:00:00Z");
        saveDecided(acme, ApplicationStatus.ACCEPTED, applied, Duration.ofDays(4));
        saveDecided(acme, ApplicationStatus.REJECTED, applied, Duration.ofHours(36));
        saveDecided(globex, ApplicationStatus.REJECTED, applied, Duration.ofDays(10));
        saveInterview(acme, Instant.now().plus(Duration.ofDays(2)));
        saveInterview(acme, Instant.now().minus(Duration.ofDays(2)));
        saveInterview(globex, Instant.now().plus(Duration.ofHours(3)));
        save(acme, ApplicationStatus.ACCEPTED, null);

        stats("SERVICE", acme).andExpect(status().isOk())
                .andExpect(jsonPath("$.averageDecisionDays").value(2.8))
                .andExpect(jsonPath("$.upcomingInterviews").value(1));
        stats("SERVICE", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.averageDecisionDays").value(5.2))
                .andExpect(jsonPath("$.upcomingInterviews").value(2));
    }

    @Test
    void noDecisionMeansNullDelayAndNoInterviewMeansZero() throws Exception {
        save(acme, ApplicationStatus.RECEIVED, null);

        stats("SERVICE", acme).andExpect(jsonPath("$.averageDecisionDays").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.upcomingInterviews").value(0));
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
