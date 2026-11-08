package com.smartrecruit.application.controller;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.StatusHistoryEntry;
import com.smartrecruit.application.support.ApiTestSupport;
import com.smartrecruit.application.support.FakeServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** EXT-APP-01 — cover letter, deadline check at application time and decision message for the candidate. */
class CoverLetterAndDecisionIntegrationTest extends ApiTestSupport {

    private final UUID candidateId = UUID.randomUUID();
    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void stubs() {
        stubJob(jobId, companyId, "PUBLISHED", Instant.parse("2026-01-01T10:00:00Z"));
        stubReadiness(candidateId, true, true, null);
        stubCompanyOf(recruiterId, companyId);
        FakeServices.on("POST /internal/v1/audit-events", 201, "");
    }

    private ResultActions apply(String body) throws Exception {
        return mockMvc.perform(as(post("/api/v1/applications"), candidateId, "CANDIDATE")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    @Test
    void coverLetterIsStoredAndReturned() throws Exception {
        apply("{\"jobId\":\"%s\",\"coverLetter\":\"  Je suis motivé par ce poste.  \"}".formatted(jobId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverLetter").value("Je suis motivé par ce poste."))
                .andExpect(jsonPath("$.interview").value(nullValue()))
                .andExpect(jsonPath("$.notesCount").value(nullValue()))
                .andExpect(jsonPath("$.averageRating").value(nullValue()));

        assertThat(applications.findAll().getFirst().getCoverLetter()).isEqualTo("Je suis motivé par ce poste.");
    }

    @Test
    void blankCoverLetterIsStoredAsNull() throws Exception {
        apply("{\"jobId\":\"%s\",\"coverLetter\":\"   \"}".formatted(jobId)).andExpect(status().isCreated());

        assertThat(applications.findAll().getFirst().getCoverLetter()).isNull();
    }

    @Test
    void tooLongCoverLetterGets400() throws Exception {
        apply("{\"jobId\":\"%s\",\"coverLetter\":\"%s\"}".formatted(jobId, "a".repeat(5001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.coverLetter").exists());
        assertThat(applications.count()).isZero();
    }

    @Test
    void passedDeadlineGets409EvenIfStillPublished() throws Exception {
        stubJob(jobId, companyId, "PUBLISHED", Instant.now(), today().minusDays(1));

        apply("{\"jobId\":\"%s\"}".formatted(jobId)).andExpect(status().isConflict());
        assertThat(applications.count()).isZero();
    }

    @Test
    void deadlineDayItselfStillAcceptsApplications() throws Exception {
        stubJob(jobId, companyId, "PUBLISHED", Instant.now(), today());

        apply("{\"jobId\":\"%s\"}".formatted(jobId)).andExpect(status().isCreated());
    }

    @Test
    void decisionMessageIsRecordedAndVisibleToTheCandidate() throws Exception {
        Application application = Application.builder().id(UUID.randomUUID()).jobId(jobId).candidateId(candidateId)
                .companyId(companyId).status(ApplicationStatus.INTERVIEW).appliedAt(Instant.now())
                .coverLetter("Ma lettre").build();
        application.getStatusHistory().add(new StatusHistoryEntry(ApplicationStatus.RECEIVED, Instant.now(), candidateId));
        applications.save(application);

        mockMvc.perform(as(patch("/api/v1/applications/" + application.getId() + "/status"), recruiterId, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"message\":\"Profil intéressant, mais le poste demande plus d'expérience.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory[1].message").value("Profil intéressant, mais le poste demande plus d'expérience."))
                .andExpect(jsonPath("$.notesCount").value(0));

        mockMvc.perform(as(get("/api/v1/applications/me"), candidateId, "CANDIDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].coverLetter").value("Ma lettre"))
                .andExpect(jsonPath("$[0].statusHistory[0].message").value(nullValue()))
                .andExpect(jsonPath("$[0].statusHistory[1].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].statusHistory[1].message").value("Profil intéressant, mais le poste demande plus d'expérience."))
                .andExpect(jsonPath("$[0].notesCount").value(nullValue()))
                .andExpect(jsonPath("$[0].averageRating").value(nullValue()));
    }

    @Test
    void tooLongMessageGets400() throws Exception {
        Application application = applications.save(Application.builder().id(UUID.randomUUID()).jobId(jobId)
                .candidateId(candidateId).companyId(companyId).status(ApplicationStatus.RECEIVED).appliedAt(Instant.now()).build());

        mockMvc.perform(as(patch("/api/v1/applications/" + application.getId() + "/status"), recruiterId, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\",\"message\":\"%s\"}".formatted("m".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.message").exists());
    }
}
