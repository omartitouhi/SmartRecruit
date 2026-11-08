package com.smartrecruit.job.controller;

import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.model.SavedJob;
import com.smartrecruit.job.repository.SavedJobRepository;
import com.smartrecruit.job.service.SavedJobService;
import com.smartrecruit.job.support.JobApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EXT-SAVED-01 — candidates bookmark offers: PUT/DELETE /api/v1/jobs/{id}/saved, GET /api/v1/jobs/saved,
 * the {@code saved} flag on search and detail, and the internal erasure for the right to be forgotten.
 */
class SavedJobIntegrationTest extends JobApiTest {

    private final UUID candidateId = UUID.randomUUID();
    private final UUID otherCandidate = UUID.randomUUID();
    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @Autowired
    private SavedJobRepository savedJobs;

    @BeforeEach
    void clean() {
        savedJobs.deleteAll();
        directoryKnows(companyId, "Acme");
    }

    private ResultActions saveAs(UUID accountId, String role, UUID jobId) throws Exception {
        return mockMvc.perform(put("/api/v1/jobs/" + jobId + "/saved").with(as(accountId, role)));
    }

    private ResultActions save(UUID jobId) throws Exception {
        return saveAs(candidateId, "CANDIDATE", jobId);
    }

    private ResultActions unsave(UUID jobId) throws Exception {
        return mockMvc.perform(delete("/api/v1/jobs/" + jobId + "/saved").with(as(candidateId, "CANDIDATE")));
    }

    private ResultActions mySaved() throws Exception {
        return mockMvc.perform(get("/api/v1/jobs/saved").with(as(candidateId, "CANDIDATE")));
    }

    @Test
    void savingIsIdempotentAndListedNewestFirst() throws Exception {
        Job first = saveJob(companyId, "Première", JobStatus.PUBLISHED);
        Job second = saveJob(companyId, "Deuxième", JobStatus.PUBLISHED);

        save(first.getId()).andExpect(status().isNoContent());
        save(first.getId()).andExpect(status().isNoContent());
        save(second.getId()).andExpect(status().isNoContent());

        assertThat(savedJobs.countByCandidateId(candidateId)).isEqualTo(2);
        mySaved().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Deuxième"))
                .andExpect(jsonPath("$[0].saved").value(true))
                .andExpect(jsonPath("$[0].companyName").value("Acme"))
                .andExpect(jsonPath("$[1].id").value(first.getId().toString()));
    }

    @Test
    void onlyPublishedOffersCanBeSaved() throws Exception {
        for (JobStatus status : new JobStatus[]{JobStatus.DRAFT, JobStatus.CLOSED, JobStatus.MODERATED}) {
            save(saveJob(companyId, "Offre " + status, status).getId()).andExpect(status().isNotFound());
        }
        save(UUID.randomUUID()).andExpect(status().isNotFound());
        assertThat(savedJobs.count()).isZero();
    }

    @Test
    void listKeepsClosedOffersAndHidesUnavailableOnes() throws Exception {
        Job closed = saveJob(companyId, "Clôturée", JobStatus.PUBLISHED);
        Job moderated = saveJob(companyId, "Modérée", JobStatus.PUBLISHED);
        Job deleted = saveJob(companyId, "Supprimée", JobStatus.PUBLISHED);
        save(closed.getId()).andExpect(status().isNoContent());
        save(moderated.getId()).andExpect(status().isNoContent());
        save(deleted.getId()).andExpect(status().isNoContent());

        closed.setStatus(JobStatus.CLOSED);
        moderated.setStatus(JobStatus.MODERATED);
        jobRepository.save(closed);
        jobRepository.save(moderated);
        jobRepository.deleteById(deleted.getId());

        mySaved().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Clôturée"))
                .andExpect(jsonPath("$[0].status").value("CLOSED"))
                .andExpect(jsonPath("$[0].moderationReason").value(nullValue()));
    }

    @Test
    void unsavingIsIdempotent() throws Exception {
        Job job = saveJob(companyId, "Offre", JobStatus.PUBLISHED);
        save(job.getId()).andExpect(status().isNoContent());

        unsave(job.getId()).andExpect(status().isNoContent());
        unsave(job.getId()).andExpect(status().isNoContent());
        unsave(UUID.randomUUID()).andExpect(status().isNoContent());

        mySaved().andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void bookmarksArePersonal() throws Exception {
        Job job = saveJob(companyId, "Offre", JobStatus.PUBLISHED);
        saveAs(otherCandidate, "CANDIDATE", job.getId()).andExpect(status().isNoContent());

        mySaved().andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/jobs/" + job.getId()).with(as(candidateId, "CANDIDATE")))
                .andExpect(jsonPath("$.saved").value(false));
    }

    @Test
    void savedFlagIsSetForCandidatesOnly() throws Exception {
        Job saved = saveJob(companyId, "Favorite", JobStatus.PUBLISHED);
        Job notSaved = saveJob(companyId, "Autre", JobStatus.PUBLISHED);
        save(saved.getId()).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/jobs").with(as(candidateId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == 'Favorite')].saved").value(true))
                .andExpect(jsonPath("$.content[?(@.title == 'Autre')].saved").value(false));
        mockMvc.perform(get("/api/v1/jobs/" + saved.getId()).with(as(candidateId, "CANDIDATE")))
                .andExpect(jsonPath("$.saved").value(true));
        mockMvc.perform(get("/api/v1/jobs/" + notSaved.getId()).with(as(candidateId, "CANDIDATE")))
                .andExpect(jsonPath("$.saved").value(false));

        mockMvc.perform(get("/api/v1/jobs/" + saved.getId()).with(as(UUID.randomUUID(), "ADMIN")))
                .andExpect(jsonPath("$.saved").value(nullValue()));
        mockMvc.perform(get("/api/v1/jobs").with(as(recruiterId, "RECRUITER")))
                .andExpect(jsonPath("$.content[0].saved").value(nullValue()));
    }

    @Test
    void bookmarksAreReservedToCandidates() throws Exception {
        Job job = saveJob(companyId, "Offre", JobStatus.PUBLISHED);

        saveAs(recruiterId, "RECRUITER", job.getId()).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/jobs/saved").with(as(recruiterId, "RECRUITER"))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/jobs/" + job.getId() + "/saved").with(as(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void numberOfBookmarksIsCapped() throws Exception {
        Instant now = Instant.now();
        IntStream.range(0, SavedJobService.MAX_SAVED).forEach(i -> savedJobs.save(SavedJob.builder()
                .id(UUID.randomUUID()).candidateId(candidateId).jobId(UUID.randomUUID()).savedAt(now).build()));
        Job job = saveJob(companyId, "Une de trop", JobStatus.PUBLISHED);

        save(job.getId()).andExpect(status().isConflict());
    }

    @Test
    void internalErasureRemovesEveryBookmarkOfTheCandidate() throws Exception {
        Job job = saveJob(companyId, "Offre", JobStatus.PUBLISHED);
        save(job.getId()).andExpect(status().isNoContent());
        saveAs(otherCandidate, "CANDIDATE", job.getId()).andExpect(status().isNoContent());

        mockMvc.perform(delete("/internal/v1/candidates/" + candidateId + "/saved-jobs")
                        .with(as(UUID.randomUUID(), "SERVICE")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/internal/v1/candidates/" + candidateId + "/saved-jobs")
                        .with(as(UUID.randomUUID(), "SERVICE")))
                .andExpect(status().isNoContent());

        assertThat(savedJobs.countByCandidateId(candidateId)).isZero();
        assertThat(savedJobs.countByCandidateId(otherCandidate)).isEqualTo(1);
        mockMvc.perform(delete("/internal/v1/candidates/" + candidateId + "/saved-jobs")
                        .with(as(candidateId, "CANDIDATE")))
                .andExpect(status().isForbidden());
    }
}
