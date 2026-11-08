package com.smartrecruit.job.controller;

import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.model.WorkMode;
import com.smartrecruit.job.service.JobExpiryService;
import com.smartrecruit.job.support.JobApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EXT-JOB-01 — work mode, salary range, application deadline (with automatic closing), number of
 * positions, matching search filters, duplication, and what other services read of it.
 */
class JobEnrichmentIntegrationTest extends JobApiTest {

    private static final String ANALYZE = "/api/v1/ai/job-analysis/analyze";

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();

    @Autowired
    private JobExpiryService expiryService;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void recruiterHasACompany() {
        memberOf(recruiterId, companyId, "Acme");
    }

    private static String offer(String extraFields) {
        return """
                {"title": "Développeur Java", "description": "Concevoir et maintenir nos microservices Spring Boot.",
                 "requiredSkills": ["Java"], "experienceLevel": "SENIOR", "contractType": "CDI", "location": "Tunis"%s}
                """.formatted(extraFields.isEmpty() ? "" : ", " + extraFields);
    }

    private ResultActions publish(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/jobs").with(as(recruiterId, "RECRUITER"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patchJob(UUID jobId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/jobs/" + jobId).with(as(recruiterId, "RECRUITER"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions changeStatus(UUID jobId, JobStatus next) throws Exception {
        return mockMvc.perform(patch("/api/v1/jobs/" + jobId + "/status").with(as(recruiterId, "RECRUITER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + next + "\"}"));
    }

    private Job saveJob(String title, JobStatus status, Consumer<Job> customize) {
        Job job = saveJob(companyId, title, status);
        customize.accept(job);
        return jobRepository.save(job);
    }

    // --- creation

    @Test
    void offerIsPublishedWithTheNewFields() throws Exception {
        LocalDate deadline = today.plusDays(10);
        publish(offer("""
                "workMode": "HYBRID", "salaryMin": 2500, "salaryMax": 3500,
                "applicationDeadline": "%s", "positions": 3""".formatted(deadline)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workMode").value("HYBRID"))
                .andExpect(jsonPath("$.salaryMin").value(2500))
                .andExpect(jsonPath("$.salaryMax").value(3500))
                .andExpect(jsonPath("$.applicationDeadline").value(deadline.toString()))
                .andExpect(jsonPath("$.positions").value(3));

        Job saved = jobRepository.findAll().getFirst();
        assertThat(saved.getWorkMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(saved.getApplicationDeadline()).isEqualTo(deadline);
    }

    @Test
    void optionalFieldsGetTheirDefaults() throws Exception {
        publish(offer(""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workMode").value("ON_SITE"))
                .andExpect(jsonPath("$.positions").value(1))
                .andExpect(jsonPath("$.salaryMin").value(nullValue()))
                .andExpect(jsonPath("$.salaryMax").value(nullValue()))
                .andExpect(jsonPath("$.applicationDeadline").value(nullValue()));
    }

    @Test
    void rowsCreatedBeforeTheFieldsReadAsOnSiteWithOnePosition() throws Exception {
        Job legacy = saveJob(companyId, "Ancienne offre", JobStatus.PUBLISHED);
        assertThat(legacy.getWorkMode()).isNull();

        mockMvc.perform(get("/api/v1/jobs/" + legacy.getId()).with(as(candidateId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workMode").value("ON_SITE"))
                .andExpect(jsonPath("$.positions").value(1));
    }

    @Test
    void invalidNewFieldsAreRejectedWithFieldErrors() throws Exception {
        publish(offer("\"salaryMin\": 4000, \"salaryMax\": 3000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.salaryMax").isNotEmpty());
        publish(offer("\"applicationDeadline\": \"%s\"".formatted(today.minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.applicationDeadline").isNotEmpty());
        publish(offer("\"positions\": 0, \"salaryMin\": -1, \"salaryMax\": 2000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.positions").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.salaryMin").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.salaryMax").isNotEmpty());
        publish(offer("\"workMode\": \"MOON\"")).andExpect(status().isBadRequest());

        assertThat(jobRepository.count()).isZero();
    }

    @Test
    void deadlineOfTodayIsAccepted() throws Exception {
        publish(offer("\"applicationDeadline\": \"%s\"".formatted(today)))
                .andExpect(status().isCreated());
    }

    // --- patch

    @Test
    void patchSetsAndRemovesTheOptionalFields() throws Exception {
        Job job = saveJob(companyId, "Développeur Java", JobStatus.PUBLISHED);
        Instant before = job.getUpdatedAt();
        LocalDate deadline = today.plusDays(5);

        patchJob(job.getId(), """
                {"workMode": "REMOTE", "salaryMin": 2000, "salaryMax": 3000, "applicationDeadline": "%s", "positions": 2}
                """.formatted(deadline))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workMode").value("REMOTE"))
                .andExpect(jsonPath("$.salaryMax").value(3000))
                .andExpect(jsonPath("$.applicationDeadline").value(deadline.toString()))
                .andExpect(jsonPath("$.positions").value(2));
        assertThat(reload(job.getId()).getUpdatedAt()).isAfter(before);

        patchJob(job.getId(), "{\"salaryMax\": null, \"applicationDeadline\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryMin").value(2000))
                .andExpect(jsonPath("$.salaryMax").value(nullValue()))
                .andExpect(jsonPath("$.applicationDeadline").value(nullValue()));
        Job reloaded = reload(job.getId());
        assertThat(reloaded.getSalaryMax()).isNull();
        assertThat(reloaded.getApplicationDeadline()).isNull();
    }

    @Test
    void patchOfTheNewFieldsDoesNotAskForANewAnalysis() throws Exception {
        Job job = saveJob(companyId, "Développeur Java", JobStatus.PUBLISHED);

        patchJob(job.getId(), "{\"workMode\": \"REMOTE\", \"positions\": 4}").andExpect(status().isOk());

        assertThat(BACKENDS.callsTo(ANALYZE)).isEmpty();
    }

    @Test
    void patchChecksTheSalaryRangeAgainstTheStoredBound() throws Exception {
        Job job = saveJob("Développeur Java", JobStatus.PUBLISHED, j -> {
            j.setSalaryMin(3000);
            j.setSalaryMax(4000);
        });

        patchJob(job.getId(), "{\"salaryMax\": 2500}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.salaryMax").isNotEmpty());
        patchJob(job.getId(), "{\"salaryMin\": 4500}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.salaryMax").isNotEmpty());
        assertThat(reload(job.getId()).getSalaryMax()).isEqualTo(4000);
    }

    @Test
    void patchRejectsAPastDeadlineAndRemovingMandatoryNewFields() throws Exception {
        Job job = saveJob(companyId, "Développeur Java", JobStatus.PUBLISHED);

        patchJob(job.getId(), "{\"applicationDeadline\": \"%s\"}".formatted(today.minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.applicationDeadline").isNotEmpty());
        patchJob(job.getId(), "{\"workMode\": null, \"positions\": null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.workMode").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.positions").isNotEmpty());
    }

    // --- search filters

    @Test
    void searchFiltersOnWorkModeAndMinimumSalary() throws Exception {
        saveJob("Sur site ancien", JobStatus.PUBLISHED, j -> { });
        saveJob("Télétravail bien payé", JobStatus.PUBLISHED, j -> {
            j.setWorkMode(WorkMode.REMOTE);
            j.setSalaryMin(3000);
            j.setSalaryMax(5000);
        });
        saveJob("Hybride minimum seul", JobStatus.PUBLISHED, j -> {
            j.setWorkMode(WorkMode.HYBRID);
            j.setSalaryMin(4500);
        });
        saveJob("Sur site modeste", JobStatus.PUBLISHED, j -> {
            j.setWorkMode(WorkMode.ON_SITE);
            j.setSalaryMin(1500);
            j.setSalaryMax(2000);
        });

        mockMvc.perform(get("/api/v1/jobs?workMode=REMOTE").with(as(candidateId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(containsInAnyOrder("Télétravail bien payé")));
        mockMvc.perform(get("/api/v1/jobs?workMode=ON_SITE").with(as(candidateId, "CANDIDATE")))
                .andExpect(jsonPath("$.content[*].title")
                        .value(containsInAnyOrder("Sur site ancien", "Sur site modeste")));
        mockMvc.perform(get("/api/v1/jobs?minSalary=4000").with(as(candidateId, "CANDIDATE")))
                .andExpect(jsonPath("$.content[*].title")
                        .value(containsInAnyOrder("Télétravail bien payé", "Hybride minimum seul")));
        mockMvc.perform(get("/api/v1/jobs?minSalary=4000&workMode=HYBRID").with(as(candidateId, "CANDIDATE")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/jobs?minSalary=-1").with(as(candidateId, "CANDIDATE")))
                .andExpect(status().isBadRequest());
    }

    // --- automatic closing

    @Test
    void expiredOffersAreClosedAndCannotBeReopened() throws Exception {
        Job expired = saveJob("Expirée", JobStatus.PUBLISHED, j -> j.setApplicationDeadline(today.minusDays(1)));
        Job lastDay = saveJob("Dernier jour", JobStatus.PUBLISHED, j -> j.setApplicationDeadline(today));
        Job draft = saveJob("Brouillon expiré", JobStatus.DRAFT, j -> j.setApplicationDeadline(today.minusDays(3)));

        assertThat(expiryService.closeExpiredOffers()).isEqualTo(1);

        Job closed = reload(expired.getId());
        assertThat(closed.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(closed.getUpdatedAt()).isAfter(Instant.parse("2026-10-01T09:00:00Z"));
        assertThat(reload(lastDay.getId()).getStatus()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(reload(draft.getId()).getStatus()).isEqualTo(JobStatus.DRAFT);

        changeStatus(expired.getId(), JobStatus.PUBLISHED).andExpect(status().isConflict());
        changeStatus(draft.getId(), JobStatus.PUBLISHED).andExpect(status().isConflict());
        assertThat(reload(expired.getId()).getStatus()).isEqualTo(JobStatus.CLOSED);

        patchJob(draft.getId(), "{\"applicationDeadline\": null}").andExpect(status().isOk());
        changeStatus(draft.getId(), JobStatus.PUBLISHED).andExpect(status().isOk());
    }

    // --- duplicate

    @Test
    void duplicateCopiesTheOfferAsADraft() throws Exception {
        Job source = saveJob("Développeur Java", JobStatus.CLOSED, j -> {
            j.setWorkMode(WorkMode.REMOTE);
            j.setSalaryMin(2000);
            j.setSalaryMax(2500);
            j.setPositions(2);
            j.setApplicationDeadline(today.minusDays(2));
        });

        String id = mockMvc.perform(post("/api/v1/jobs/" + source.getId() + "/duplicate").with(as(recruiterId, "RECRUITER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Développeur Java (copie)"))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.applicationDeadline").value(nullValue()))
                .andExpect(jsonPath("$.workMode").value("REMOTE"))
                .andExpect(jsonPath("$.salaryMax").value(2500))
                .andExpect(jsonPath("$.positions").value(2))
                .andExpect(jsonPath("$.requiredSkills[0]").value("Java"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        assertThat(id).isNotEqualTo(source.getId().toString());
        assertThat(jobRepository.count()).isEqualTo(2);
        BACKENDS.awaitCallsTo(ANALYZE, 1);
        changeStatus(UUID.fromString(id), JobStatus.PUBLISHED).andExpect(status().isOk());
    }

    @Test
    void duplicateTruncatesALongTitle() throws Exception {
        Job source = saveJob("x".repeat(255), JobStatus.PUBLISHED, j -> { });

        mockMvc.perform(post("/api/v1/jobs/" + source.getId() + "/duplicate").with(as(recruiterId, "RECRUITER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("x".repeat(255 - 8) + " (copie)"));
    }

    @Test
    void duplicateIsReservedToTheOwningCompanyAndRefusedForModeratedOffers() throws Exception {
        Job moderated = saveJob(companyId, "Retirée", JobStatus.MODERATED);
        Job other = saveJob(UUID.randomUUID(), "Autre entreprise", JobStatus.PUBLISHED);

        mockMvc.perform(post("/api/v1/jobs/" + moderated.getId() + "/duplicate").with(as(recruiterId, "RECRUITER")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/jobs/" + other.getId() + "/duplicate").with(as(recruiterId, "RECRUITER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/jobs/" + UUID.randomUUID() + "/duplicate").with(as(recruiterId, "RECRUITER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/jobs/" + other.getId() + "/duplicate").with(as(candidateId, "CANDIDATE")))
                .andExpect(status().isForbidden());
        assertThat(jobRepository.count()).isEqualTo(2);
    }

    // --- internal views

    @Test
    void internalViewCarriesTheDeadline() throws Exception {
        LocalDate deadline = today.plusDays(3);
        Job job = saveJob("Développeur Java", JobStatus.PUBLISHED, j -> j.setApplicationDeadline(deadline));

        mockMvc.perform(get("/internal/v1/jobs/" + job.getId()).with(as(UUID.randomUUID(), "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationDeadline").value(deadline.toString()));
    }

    @Test
    void statsCountPublishedOffersClosingWithinAWeek() throws Exception {
        saveJob("Aujourd'hui", JobStatus.PUBLISHED, j -> j.setApplicationDeadline(today));
        saveJob("Dans 7 jours", JobStatus.PUBLISHED, j -> j.setApplicationDeadline(today.plusDays(7)));
        saveJob("Dans 8 jours", JobStatus.PUBLISHED, j -> j.setApplicationDeadline(today.plusDays(8)));
        saveJob("Brouillon", JobStatus.DRAFT, j -> j.setApplicationDeadline(today.plusDays(1)));
        saveJob("Sans date", JobStatus.PUBLISHED, j -> { });
        Job other = saveJob(UUID.randomUUID(), "Autre entreprise", JobStatus.PUBLISHED);
        other.setApplicationDeadline(today.plusDays(2));
        jobRepository.save(other);

        mockMvc.perform(get("/internal/v1/jobs/stats").with(as(UUID.randomUUID(), "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingSoon").value(3));
        mockMvc.perform(get("/internal/v1/jobs/stats?companyId=" + companyId).with(as(UUID.randomUUID(), "SERVICE")))
                .andExpect(jsonPath("$.closingSoon").value(2))
                .andExpect(jsonPath("$.published").value(4));
    }
}
