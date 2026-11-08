package com.smartrecruit.application.controller;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.StatusHistoryEntry;
import com.smartrecruit.application.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-APP-05 — DELETE /api/v1/applications/{id}. */
class WithdrawIntegrationTest extends ApiTestSupport {

    private final UUID candidateId = UUID.randomUUID();
    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void stubs() {
        stubJob(jobId, companyId, "PUBLISHED", Instant.now());
        stubCompanyOf(recruiterId, companyId);
    }

    private Application save(ApplicationStatus status) {
        Application application = Application.builder().id(UUID.randomUUID()).jobId(jobId).candidateId(candidateId)
                .companyId(companyId).status(status).appliedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        application.getStatusHistory().add(new StatusHistoryEntry(ApplicationStatus.RECEIVED, application.getAppliedAt(), candidateId));
        return applications.save(application);
    }

    private ResultActions withdraw(UUID caller, String role, UUID id) throws Exception {
        return mockMvc.perform(as(delete("/api/v1/applications/" + id), caller, role));
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = {"RECEIVED", "IN_REVIEW"})
    void withdrawableStatusBecomesWithdrawnWithHistory(ApplicationStatus from) throws Exception {
        Application application = save(from);

        withdraw(candidateId, "CANDIDATE", application.getId()).andExpect(status().isNoContent());

        Application stored = applications.findById(application.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(stored.getStatusHistory()).extracting(StatusHistoryEntry::getStatus)
                .containsExactly(ApplicationStatus.RECEIVED, ApplicationStatus.WITHDRAWN);
        assertThat(stored.getStatusHistory().getLast().getChangedBy()).isEqualTo(candidateId);
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = {"INTERVIEW", "ACCEPTED", "REJECTED", "WITHDRAWN"})
    void otherStatusesGet409(ApplicationStatus from) throws Exception {
        Application application = save(from);

        withdraw(candidateId, "CANDIDATE", application.getId()).andExpect(status().isConflict());

        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(from);
    }

    @Test
    void withdrawnApplicationDisappearsFromRecruiterListButStaysInMine() throws Exception {
        Application application = save(ApplicationStatus.RECEIVED);

        withdraw(candidateId, "CANDIDATE", application.getId()).andExpect(status().isNoContent());

        mockMvc.perform(as(get("/api/v1/applications").param("jobId", jobId.toString()), recruiterId, "RECRUITER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(get("/api/v1/applications/me"), candidateId, "CANDIDATE"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("WITHDRAWN"));
    }

    @Test
    void anotherCandidateGets403() throws Exception {
        Application application = save(ApplicationStatus.RECEIVED);

        withdraw(UUID.randomUUID(), "CANDIDATE", application.getId()).andExpect(status().isForbidden());

        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.RECEIVED);
    }

    @Test
    void unknownApplicationGets404() throws Exception {
        withdraw(candidateId, "CANDIDATE", UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void recruiterAndAdminGet403() throws Exception {
        Application application = save(ApplicationStatus.RECEIVED);

        withdraw(recruiterId, "RECRUITER", application.getId()).andExpect(status().isForbidden());
        withdraw(candidateId, "ADMIN", application.getId()).andExpect(status().isForbidden());
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(delete("/api/v1/applications/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedIdGets400() throws Exception {
        mockMvc.perform(as(delete("/api/v1/applications/not-a-uuid"), candidateId, "CANDIDATE"))
                .andExpect(status().isBadRequest());
    }
}
