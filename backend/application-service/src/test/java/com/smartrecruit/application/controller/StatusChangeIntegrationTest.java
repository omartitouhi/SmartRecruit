package com.smartrecruit.application.controller;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.StatusHistoryEntry;
import com.smartrecruit.application.support.ApiTestSupport;
import com.smartrecruit.application.support.FakeServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-APP-04 — PATCH /api/v1/applications/{id}/status. */
class StatusChangeIntegrationTest extends ApiTestSupport {

    private static final String AUDIT = "POST /internal/v1/audit-events";

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void recruiterOfCompany() {
        stubCompanyOf(recruiterId, companyId);
        FakeServices.on(AUDIT, 201, "");
    }

    private Application save(ApplicationStatus status) {
        Application application = Application.builder().id(UUID.randomUUID()).jobId(UUID.randomUUID())
                .candidateId(UUID.randomUUID()).companyId(companyId).status(status)
                .appliedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        application.getStatusHistory().add(new StatusHistoryEntry(ApplicationStatus.RECEIVED, application.getAppliedAt(),
                application.getCandidateId()));
        return applications.save(application);
    }

    private ResultActions change(UUID caller, String role, UUID id, String body) throws Exception {
        return mockMvc.perform(as(patch("/api/v1/applications/" + id + "/status"), caller, role)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions change(UUID id, ApplicationStatus target) throws Exception {
        return change(recruiterId, "RECRUITER", id, "{\"status\":\"%s\"}".formatted(target));
    }

    static Stream<Object[]> everyTransition() {
        List<Object[]> pairs = new ArrayList<>();
        for (ApplicationStatus from : ApplicationStatus.values()) {
            for (ApplicationStatus to : ApplicationStatus.values()) {
                boolean allowed = to != ApplicationStatus.WITHDRAWN && from.canTransitionTo(to);
                pairs.add(new Object[]{from, to, allowed});
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("everyTransition")
    void transitionTableIsEnforced(ApplicationStatus from, ApplicationStatus to, boolean allowed) throws Exception {
        Application application = save(from);

        change(application.getId(), to).andExpect(status().is(allowed ? 200 : 400));

        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(allowed ? to : from);
    }

    @Test
    void allowedChangeAppendsHistoryWithRecruiterAndReturnsApplication() throws Exception {
        Application application = save(ApplicationStatus.RECEIVED);

        change(application.getId(), ApplicationStatus.IN_REVIEW).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(application.getId().toString()))
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.statusHistory.length()").value(2))
                .andExpect(jsonPath("$.statusHistory[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$.statusHistory[1].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.statusHistory[1].changedBy").value(recruiterId.toString()))
                .andExpect(jsonPath("$.statusHistory[1].changedAt").exists());
    }

    @Test
    void rejectedCannotGoBackToReceived() throws Exception {
        Application application = save(ApplicationStatus.REJECTED);

        change(application.getId(), ApplicationStatus.RECEIVED).andExpect(status().isBadRequest());
    }

    @Test
    void withdrawnIsReservedForTheCandidate() throws Exception {
        Application application = save(ApplicationStatus.RECEIVED);

        change(application.getId(), ApplicationStatus.WITHDRAWN).andExpect(status().isBadRequest());
        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.RECEIVED);
    }

    @Test
    void auditEventIsSentWithActorAndResource() throws Exception {
        Application application = save(ApplicationStatus.RECEIVED);

        change(application.getId(), ApplicationStatus.IN_REVIEW).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).until(() -> !FakeServices.calls(AUDIT).isEmpty());
        var call = FakeServices.calls(AUDIT).getFirst();
        assertThat(call.role()).isEqualTo("SERVICE");
        assertThat(call.body()).contains("\"action\":\"APPLICATION_STATUS_CHANGED\"")
                .contains(recruiterId.toString()).contains(application.getId().toString());
    }

    @Test
    void auditFailureDoesNotFailTheChange() throws Exception {
        FakeServices.on(AUDIT, 500, "");
        Application application = save(ApplicationStatus.RECEIVED);

        change(application.getId(), ApplicationStatus.IN_REVIEW).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).until(() -> !FakeServices.calls(AUDIT).isEmpty());
        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.IN_REVIEW);
    }

    @Test
    void noAuditEventForForbiddenTransition() throws Exception {
        Application application = save(ApplicationStatus.REJECTED);

        change(application.getId(), ApplicationStatus.RECEIVED).andExpect(status().isBadRequest());

        Thread.sleep(200);
        assertThat(FakeServices.calls(AUDIT)).isEmpty();
    }

    @Test
    void recruiterOfAnotherCompanyGets403() throws Exception {
        UUID stranger = UUID.randomUUID();
        stubCompanyOf(stranger, UUID.randomUUID());
        Application application = save(ApplicationStatus.RECEIVED);

        change(stranger, "RECRUITER", application.getId(), "{\"status\":\"IN_REVIEW\"}").andExpect(status().isForbidden());
        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.RECEIVED);
    }

    @Test
    void unknownApplicationGets404() throws Exception {
        change(UUID.randomUUID(), ApplicationStatus.IN_REVIEW).andExpect(status().isNotFound());
    }

    @Test
    void candidateAndAdminGet403() throws Exception {
        Application application = save(ApplicationStatus.RECEIVED);

        change(recruiterId, "CANDIDATE", application.getId(), "{\"status\":\"IN_REVIEW\"}").andExpect(status().isForbidden());
        change(recruiterId, "ADMIN", application.getId(), "{\"status\":\"IN_REVIEW\"}").andExpect(status().isForbidden());
    }

    @Test
    void missingStatusGets400WithFieldErrors() throws Exception {
        Application application = save(ApplicationStatus.RECEIVED);

        change(recruiterId, "RECRUITER", application.getId(), "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void unknownStatusValueGets400() throws Exception {
        Application application = save(ApplicationStatus.RECEIVED);

        change(recruiterId, "RECRUITER", application.getId(), "{\"status\":\"HIRED\"}").andExpect(status().isBadRequest());
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(patch("/api/v1/applications/" + UUID.randomUUID() + "/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isUnauthorized());
    }
}
