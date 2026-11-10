package com.smartrecruit.job.controller;

import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.support.JobApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DELETE /api/v1/jobs/{id}: only a draft of the recruiter's own company can be deleted. */
class JobDeleteIntegrationTest extends JobApiTest {

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void recruiterHasACompany() {
        memberOf(recruiterId, companyId, "Acme");
    }

    private ResultActions deleteJob(UUID jobId, UUID accountId, String role) throws Exception {
        return mockMvc.perform(delete("/api/v1/jobs/" + jobId).with(as(accountId, role)));
    }

    @Test
    void draftIsDeleted() throws Exception {
        Job draft = saveJob(companyId, "Brouillon", JobStatus.DRAFT);

        deleteJob(draft.getId(), recruiterId, "RECRUITER").andExpect(status().isNoContent());
        assertThat(jobRepository.existsById(draft.getId())).isFalse();
    }

    @Test
    void publishedClosedAndModeratedOffersAreKept() throws Exception {
        for (JobStatus kept : new JobStatus[]{JobStatus.PUBLISHED, JobStatus.CLOSED, JobStatus.MODERATED}) {
            Job job = saveJob(companyId, "Offre " + kept, kept);
            deleteJob(job.getId(), recruiterId, "RECRUITER").andExpect(status().isConflict());
            assertThat(reload(job.getId()).getStatus()).isEqualTo(kept);
        }
    }

    @Test
    void draftOfAnotherCompanyGets403AndUnknownOfferGets404() throws Exception {
        Job foreign = saveJob(UUID.randomUUID(), "Chez Globex", JobStatus.DRAFT);

        deleteJob(foreign.getId(), recruiterId, "RECRUITER").andExpect(status().isForbidden());
        assertThat(jobRepository.existsById(foreign.getId())).isTrue();
        deleteJob(UUID.randomUUID(), recruiterId, "RECRUITER").andExpect(status().isNotFound());
    }

    @Test
    void candidatesCannotDeleteOffers() throws Exception {
        Job draft = saveJob(companyId, "Brouillon", JobStatus.DRAFT);

        deleteJob(draft.getId(), UUID.randomUUID(), "CANDIDATE").andExpect(status().isForbidden());
        assertThat(jobRepository.existsById(draft.getId())).isTrue();
    }
}
