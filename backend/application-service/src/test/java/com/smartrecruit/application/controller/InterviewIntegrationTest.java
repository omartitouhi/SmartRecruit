package com.smartrecruit.application.controller;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.Interview;
import com.smartrecruit.application.model.InterviewMode;
import com.smartrecruit.application.support.ApiTestSupport;
import com.smartrecruit.application.support.FakeServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** EXT-APP-02 — interviews planned with the status change or afterwards, and the upcoming agenda. */
class InterviewIntegrationTest extends ApiTestSupport {

    private static final String AUDIT = "POST /internal/v1/audit-events";

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void recruiterOfCompany() {
        stubCompanyOf(recruiterId, companyId);
        FakeServices.on(AUDIT, 201, "");
    }

    private Application save(UUID company, ApplicationStatus status, Instant interviewAt) {
        return applications.save(Application.builder().id(UUID.randomUUID()).jobId(UUID.randomUUID())
                .candidateId(UUID.randomUUID()).companyId(company).status(status).appliedAt(Instant.now())
                .interview(interviewAt == null ? null
                        : Interview.builder().scheduledAt(interviewAt).durationMinutes(45).mode(InterviewMode.VIDEO)
                        .location("https://meet.example/abc").build())
                .build());
    }

    private ResultActions changeStatus(UUID id, String body) throws Exception {
        return mockMvc.perform(as(patch("/api/v1/applications/" + id + "/status"), recruiterId, "RECRUITER")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions schedule(UUID caller, UUID id, String body) throws Exception {
        return mockMvc.perform(as(put("/api/v1/applications/" + id + "/interview"), caller, "RECRUITER")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String interview(Instant at, String extra) {
        return "{\"scheduledAt\":\"%s\",\"mode\":\"ON_SITE\"%s}".formatted(at, extra);
    }

    @Test
    void movingToInterviewWithAnInterviewPlansItWithDefaultDuration() throws Exception {
        Application application = save(companyId, ApplicationStatus.IN_REVIEW, null);

        changeStatus(application.getId(), "{\"status\":\"INTERVIEW\",\"interview\":%s}"
                .formatted(interview(tomorrow, ",\"location\":\"Siège, Tunis\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"))
                .andExpect(jsonPath("$.interview.scheduledAt").value(tomorrow.toString()))
                .andExpect(jsonPath("$.interview.durationMinutes").value(60))
                .andExpect(jsonPath("$.interview.mode").value("ON_SITE"))
                .andExpect(jsonPath("$.interview.location").value("Siège, Tunis"));

        await().atMost(Duration.ofSeconds(10)).until(() -> FakeServices.calls(AUDIT).stream()
                .anyMatch(call -> call.body().contains("INTERVIEW_SCHEDULED") && call.body().contains(application.getId().toString())));
    }

    @Test
    void movingToInterviewWithoutAnInterviewStaysPossible() throws Exception {
        Application application = save(companyId, ApplicationStatus.IN_REVIEW, null);

        changeStatus(application.getId(), "{\"status\":\"INTERVIEW\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.interview").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void interviewWithAnotherStatusGets400() throws Exception {
        Application application = save(companyId, ApplicationStatus.RECEIVED, null);

        changeStatus(application.getId(), "{\"status\":\"IN_REVIEW\",\"interview\":%s}".formatted(interview(tomorrow, "")))
                .andExpect(status().isBadRequest());
        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.RECEIVED);
    }

    @Test
    void pastDateGets400AndNothingChanges() throws Exception {
        Application application = save(companyId, ApplicationStatus.IN_REVIEW, null);

        changeStatus(application.getId(), "{\"status\":\"INTERVIEW\",\"interview\":%s}"
                .formatted(interview(Instant.now().minus(1, ChronoUnit.HOURS), "")))
                .andExpect(status().isBadRequest());
        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.IN_REVIEW);
    }

    @Test
    void invalidInterviewFieldsGet400WithFieldErrors() throws Exception {
        Application application = save(companyId, ApplicationStatus.INTERVIEW, null);

        schedule(recruiterId, application.getId(), interview(tomorrow, ",\"durationMinutes\":10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").exists());
        schedule(recruiterId, application.getId(), "{\"scheduledAt\":\"%s\"}".formatted(tomorrow))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.mode").exists());
        schedule(recruiterId, application.getId(), interview(tomorrow, ",\"location\":\"%s\"".formatted("x".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.location").exists());
    }

    @Test
    void putReschedulesAnInterview() throws Exception {
        Application application = save(companyId, ApplicationStatus.INTERVIEW, tomorrow);
        Instant later = tomorrow.plus(2, ChronoUnit.DAYS);

        schedule(recruiterId, application.getId(), "{\"scheduledAt\":\"%s\",\"durationMinutes\":90,\"mode\":\"PHONE\"}".formatted(later))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interview.scheduledAt").value(later.toString()))
                .andExpect(jsonPath("$.interview.durationMinutes").value(90))
                .andExpect(jsonPath("$.interview.mode").value("PHONE"))
                .andExpect(jsonPath("$.interview.location").value(org.hamcrest.Matchers.nullValue()));

        Interview stored = applications.findById(application.getId()).orElseThrow().getInterview();
        assertThat(stored.getScheduledAt()).isEqualTo(later);
        await().atMost(Duration.ofSeconds(10)).until(() -> FakeServices.calls(AUDIT).stream()
                .anyMatch(call -> call.body().contains("INTERVIEW_SCHEDULED") && call.body().contains(application.getId().toString())));
    }

    @Test
    void putOutsideInterviewStatusGets409() throws Exception {
        Application application = save(companyId, ApplicationStatus.IN_REVIEW, null);

        schedule(recruiterId, application.getId(), interview(tomorrow, "")).andExpect(status().isConflict());
    }

    @Test
    void putByAnotherCompanyGets403AndUnknownGets404() throws Exception {
        UUID stranger = UUID.randomUUID();
        stubCompanyOf(stranger, UUID.randomUUID());
        Application application = save(companyId, ApplicationStatus.INTERVIEW, null);

        schedule(stranger, application.getId(), interview(tomorrow, "")).andExpect(status().isForbidden());
        schedule(recruiterId, UUID.randomUUID(), interview(tomorrow, "")).andExpect(status().isNotFound());
    }

    @Test
    void putIsReservedForRecruiters() throws Exception {
        Application application = save(companyId, ApplicationStatus.INTERVIEW, null);

        for (String role : new String[]{"CANDIDATE", "ADMIN"}) {
            mockMvc.perform(as(put("/api/v1/applications/" + application.getId() + "/interview"), recruiterId, role)
                            .contentType(MediaType.APPLICATION_JSON).content(interview(tomorrow, "")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void upcomingListsOnlyFutureInterviewsOfTheCompanySoonestFirst() throws Exception {
        Application later = save(companyId, ApplicationStatus.INTERVIEW, tomorrow.plus(3, ChronoUnit.DAYS));
        Application sooner = save(companyId, ApplicationStatus.INTERVIEW, tomorrow);
        save(companyId, ApplicationStatus.INTERVIEW, Instant.now().minus(1, ChronoUnit.DAYS));
        save(companyId, ApplicationStatus.ACCEPTED, tomorrow);
        save(companyId, ApplicationStatus.INTERVIEW, null);
        save(UUID.randomUUID(), ApplicationStatus.INTERVIEW, tomorrow);

        mockMvc.perform(as(get("/api/v1/applications/interviews/upcoming"), recruiterId, "RECRUITER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].applicationId").value(sooner.getId().toString()))
                .andExpect(jsonPath("$[0].jobId").value(sooner.getJobId().toString()))
                .andExpect(jsonPath("$[0].candidateId").value(sooner.getCandidateId().toString()))
                .andExpect(jsonPath("$[0].scheduledAt").value(tomorrow.toString()))
                .andExpect(jsonPath("$[0].durationMinutes").value(45))
                .andExpect(jsonPath("$[0].mode").value("VIDEO"))
                .andExpect(jsonPath("$[0].location").value("https://meet.example/abc"))
                .andExpect(jsonPath("$[1].applicationId").value(later.getId().toString()));
    }

    @Test
    void upcomingIsEmptyWithoutACompanyAndReservedForRecruiters() throws Exception {
        UUID lonely = UUID.randomUUID();
        save(companyId, ApplicationStatus.INTERVIEW, tomorrow);

        mockMvc.perform(as(get("/api/v1/applications/interviews/upcoming"), lonely, "RECRUITER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(get("/api/v1/applications/interviews/upcoming"), recruiterId, "CANDIDATE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidateSeesTheirInterview() throws Exception {
        Application application = save(companyId, ApplicationStatus.INTERVIEW, tomorrow);

        mockMvc.perform(as(get("/api/v1/applications/me"), application.getCandidateId(), "CANDIDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].interview.scheduledAt").value(tomorrow.toString()))
                .andExpect(jsonPath("$[0].interview.mode").value("VIDEO"));
    }
}
