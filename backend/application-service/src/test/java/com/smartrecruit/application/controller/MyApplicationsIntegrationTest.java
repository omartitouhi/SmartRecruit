package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.MatchCriterionDto;
import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-APP-02 — GET /api/v1/applications/me. */
class MyApplicationsIntegrationTest extends ApiTestSupport {

    private final UUID candidateId = UUID.randomUUID();

    private Application save(UUID candidate, ApplicationStatus status, Instant appliedAt, MatchScoreDto score) {
        return applications.save(Application.builder().id(UUID.randomUUID()).jobId(UUID.randomUUID())
                .candidateId(candidate).companyId(UUID.randomUUID()).status(status).appliedAt(appliedAt)
                .matchScore(score).build());
    }

    @Test
    void listsOwnApplicationsNewestFirstIncludingWithdrawn() throws Exception {
        MatchScoreDto score = new MatchScoreDto(88, List.of(new MatchCriterionDto("Skills", 0.5, 44, "ok")));
        Application oldest = save(candidateId, ApplicationStatus.IN_REVIEW, Instant.parse("2026-01-01T00:00:00Z"), score);
        Application newest = save(candidateId, ApplicationStatus.WITHDRAWN, Instant.parse("2026-03-01T00:00:00Z"), null);
        Application middle = save(candidateId, ApplicationStatus.RECEIVED, Instant.parse("2026-02-01T00:00:00Z"), null);
        save(UUID.randomUUID(), ApplicationStatus.RECEIVED, Instant.parse("2026-04-01T00:00:00Z"), null);

        mockMvc.perform(as(get("/api/v1/applications/me"), candidateId, "CANDIDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(newest.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$[1].id").value(middle.getId().toString()))
                .andExpect(jsonPath("$[2].id").value(oldest.getId().toString()))
                .andExpect(jsonPath("$[2].matchScore.score").value(88.0))
                .andExpect(jsonPath("$[2].matchScore.criteria[0].label").value("Skills"))
                .andExpect(jsonPath("$[1].matchScore").doesNotExist());
    }

    @Test
    void emptyListWhenNothingApplied() throws Exception {
        mockMvc.perform(as(get("/api/v1/applications/me"), candidateId, "CANDIDATE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recruiterAndAdminGet403() throws Exception {
        mockMvc.perform(as(get("/api/v1/applications/me"), candidateId, "RECRUITER")).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/v1/applications/me"), candidateId, "ADMIN")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/v1/applications/me")).andExpect(status().isUnauthorized());
    }
}
