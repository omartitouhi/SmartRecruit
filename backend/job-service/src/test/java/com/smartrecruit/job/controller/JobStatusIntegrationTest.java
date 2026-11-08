package com.smartrecruit.job.controller;

import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.support.JobApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-JOB-03 — PATCH /api/v1/jobs/{id}/status and its transition table.
 */
class JobStatusIntegrationTest extends JobApiTest {

    private static final Instant SEEDED = Instant.parse("2026-10-01T09:00:00Z");

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void recruiterHasACompany() {
        memberOf(recruiterId, companyId, "Acme");
    }

    private ResultActions changeStatus(UUID jobId, String body, UUID accountId, String role) throws Exception {
        return mockMvc.perform(patch("/api/v1/jobs/" + jobId + "/status").with(as(accountId, role))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions changeStatus(Job job, JobStatus next) throws Exception {
        return changeStatus(job.getId(), "{\"status\": \"" + next + "\"}", recruiterId, "RECRUITER");
    }

    @Test
    void publishedOfferCanBeClosedAndReopened() throws Exception {
        Job job = saveJob(companyId, "Développeur Java", JobStatus.PUBLISHED);

        changeStatus(job, JobStatus.CLOSED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.publishedAt").value("2026-10-01T09:00:00Z"));
        Job closed = reload(job.getId());
        assertThat(closed.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(closed.getUpdatedAt()).isAfter(SEEDED);

        changeStatus(job, JobStatus.PUBLISHED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value("2026-10-01T09:00:00Z"));
        assertThat(reload(job.getId()).getStatus()).isEqualTo(JobStatus.PUBLISHED);
    }

    @Test
    void draftIsPublishedWithAPublicationDate() throws Exception {
        Job draft = saveJob(companyId, "Brouillon", JobStatus.DRAFT);

        changeStatus(draft, JobStatus.PUBLISHED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());
        assertThat(reload(draft.getId()).getPublishedAt()).isAfter(SEEDED);
    }

    @Test
    void forbiddenTransitionsGet400AndNothingChanges() throws Exception {
        Job draft = saveJob(companyId, "Brouillon", JobStatus.DRAFT);
        Job published = saveJob(companyId, "Publiée", JobStatus.PUBLISHED);
        Job closed = saveJob(companyId, "Clôturée", JobStatus.CLOSED);

        changeStatus(draft, JobStatus.CLOSED).andExpect(status().isBadRequest());
        changeStatus(draft, JobStatus.DRAFT).andExpect(status().isBadRequest());
        changeStatus(draft, JobStatus.MODERATED).andExpect(status().isBadRequest());
        changeStatus(published, JobStatus.PUBLISHED).andExpect(status().isBadRequest());
        changeStatus(published, JobStatus.DRAFT).andExpect(status().isBadRequest());
        changeStatus(published, JobStatus.MODERATED).andExpect(status().isBadRequest());
        changeStatus(closed, JobStatus.CLOSED).andExpect(status().isBadRequest());
        changeStatus(closed, JobStatus.DRAFT).andExpect(status().isBadRequest());
        changeStatus(closed, JobStatus.MODERATED).andExpect(status().isBadRequest());

        assertThat(reload(draft.getId()).getStatus()).isEqualTo(JobStatus.DRAFT);
        assertThat(reload(published.getId()).getStatus()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(reload(closed.getId()).getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(reload(published.getId()).getUpdatedAt()).isEqualTo(SEEDED);
    }

    @Test
    void moderatedOfferIsOutOfTheRecruitersHands() throws Exception {
        Job moderated = saveJob(companyId, "Modérée", JobStatus.MODERATED);

        for (JobStatus next : JobStatus.values()) {
            changeStatus(moderated, next).andExpect(status().isForbidden());
        }
        assertThat(reload(moderated.getId()).getStatus()).isEqualTo(JobStatus.MODERATED);
    }

    @Test
    void offerOfAnotherCompanyGets403() throws Exception {
        Job foreign = saveJob(UUID.randomUUID(), "Chez Globex", JobStatus.PUBLISHED);

        changeStatus(foreign, JobStatus.CLOSED).andExpect(status().isForbidden());
        assertThat(reload(foreign.getId()).getStatus()).isEqualTo(JobStatus.PUBLISHED);
    }

    @Test
    void recruiterWithoutCompanyGets403() throws Exception {
        Job job = saveJob(companyId, "Développeur Java", JobStatus.PUBLISHED);

        changeStatus(job.getId(), "{\"status\": \"CLOSED\"}", UUID.randomUUID(), "RECRUITER")
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownOfferGets404() throws Exception {
        changeStatus(UUID.randomUUID(), "{\"status\": \"CLOSED\"}", recruiterId, "RECRUITER")
                .andExpect(status().isNotFound());
    }

    @Test
    void missingOrUnknownStatusGets400() throws Exception {
        Job job = saveJob(companyId, "Développeur Java", JobStatus.PUBLISHED);

        changeStatus(job.getId(), "{}", recruiterId, "RECRUITER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").isNotEmpty());
        changeStatus(job.getId(), "{\"status\": \"ARCHIVED\"}", recruiterId, "RECRUITER")
                .andExpect(status().isBadRequest());
        changeStatus(job.getId(), "", recruiterId, "RECRUITER").andExpect(status().isBadRequest());
    }

    @Test
    void userServiceOutageGets503() throws Exception {
        Job job = saveJob(companyId, "Développeur Java", JobStatus.PUBLISHED);
        BACKENDS.respond("/internal/v1/companies/by-member/" + recruiterId, 500, "");

        changeStatus(job, JobStatus.CLOSED).andExpect(status().isServiceUnavailable());
        assertThat(reload(job.getId()).getStatus()).isEqualTo(JobStatus.PUBLISHED);
    }

    @Test
    void onlyRecruitersCanChangeTheStatus() throws Exception {
        Job job = saveJob(companyId, "Développeur Java", JobStatus.PUBLISHED);

        changeStatus(job.getId(), "{\"status\": \"CLOSED\"}", UUID.randomUUID(), "CANDIDATE")
                .andExpect(status().isForbidden());
        changeStatus(job.getId(), "{\"status\": \"CLOSED\"}", UUID.randomUUID(), "ADMIN")
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/jobs/" + job.getId() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CLOSED\"}"))
                .andExpect(status().isUnauthorized());
    }
}
