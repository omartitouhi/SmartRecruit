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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-ADMIN-02 (job side) — /internal/v1/jobs: moderation, lookup and statistics, called by other
 * services with the SERVICE identity.
 */
class InternalJobIntegrationTest extends JobApiTest {

    private static final String REASON = "Contenu discriminatoire";

    private final UUID acme = UUID.randomUUID();
    private final UUID globex = UUID.randomUUID();
    private final UUID recruiterId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private Job published;

    @BeforeEach
    void seed() {
        memberOf(recruiterId, acme, "Acme");
        published = saveJob(acme, "Développeur Java", JobStatus.PUBLISHED);
    }

    private ResultActions asService(String path) throws Exception {
        return mockMvc.perform(get(path).with(as(UUID.randomUUID(), "SERVICE")));
    }

    private ResultActions moderate(UUID jobId, String body, UUID accountId, String role) throws Exception {
        return mockMvc.perform(patch("/internal/v1/jobs/" + jobId + "/moderation").with(as(accountId, role))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions moderate(UUID jobId, String reason) throws Exception {
        return moderate(jobId, "{\"reason\": \"" + reason + "\"}", UUID.randomUUID(), "SERVICE");
    }

    private ResultActions view(String path, UUID accountId, String role) throws Exception {
        return mockMvc.perform(get(path).with(as(accountId, role)));
    }

    @Test
    void moderationWithdrawsTheOfferAndRecordsReasonAndDate() throws Exception {
        moderate(published.getId(), REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(published.getId().toString()))
                .andExpect(jsonPath("$.status").value("MODERATED"))
                .andExpect(jsonPath("$.moderationReason").value(REASON))
                .andExpect(jsonPath("$.publishedAt").value("2026-10-01T09:00:00Z"));

        Job saved = reload(published.getId());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.MODERATED);
        assertThat(saved.getModerationReason()).isEqualTo(REASON);
        assertThat(saved.getModeratedAt()).isAfter(Instant.parse("2026-10-01T09:00:00Z"));
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getModeratedAt());
    }

    @Test
    void moderatingAgainReplacesTheReason() throws Exception {
        moderate(published.getId(), REASON).andExpect(status().isOk());

        moderate(published.getId(), "Nouvelle raison").andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationReason").value("Nouvelle raison"));
    }

    @Test
    void anyStatusCanBeModerated() throws Exception {
        for (JobStatus current : new JobStatus[]{JobStatus.DRAFT, JobStatus.CLOSED}) {
            Job job = saveJob(acme, "Offre " + current, current);
            moderate(job.getId(), REASON).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MODERATED"));
        }
    }

    @Test
    void invalidModerationRequestsGet400() throws Exception {
        moderate(published.getId(), "{}", UUID.randomUUID(), "SERVICE")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").isNotEmpty());
        moderate(published.getId(), "{\"reason\": \"   \"}", UUID.randomUUID(), "SERVICE")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").isNotEmpty());
        moderate(published.getId(), "{\"reason\": \"%s\"}".formatted("r".repeat(501)), UUID.randomUUID(), "SERVICE")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").isNotEmpty());

        assertThat(reload(published.getId()).getStatus()).isEqualTo(JobStatus.PUBLISHED);
    }

    @Test
    void moderatingAnUnknownOfferGets404() throws Exception {
        moderate(UUID.randomUUID(), REASON).andExpect(status().isNotFound());
    }

    @Test
    void moderatedOfferVanishesFromCandidateSearchAndDetail() throws Exception {
        moderate(published.getId(), REASON).andExpect(status().isOk());

        view("/api/v1/jobs", candidateId, "CANDIDATE").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        view("/api/v1/jobs?keyword=java", UUID.randomUUID(), "RECRUITER").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        view("/api/v1/jobs/" + published.getId(), candidateId, "CANDIDATE").andExpect(status().isNotFound());
    }

    @Test
    void ownerAndAdminReadTheReasonOthersNever() throws Exception {
        moderate(published.getId(), REASON).andExpect(status().isOk());
        UUID globexRecruiter = UUID.randomUUID();
        memberOf(globexRecruiter, globex, "Globex");

        view("/api/v1/jobs/" + published.getId(), recruiterId, "RECRUITER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MODERATED"))
                .andExpect(jsonPath("$.moderationReason").value(REASON));
        view("/api/v1/jobs/" + published.getId(), adminId, "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationReason").value(REASON));
        view("/api/v1/jobs?companyId=me", recruiterId, "RECRUITER").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].moderationReason").value(REASON));
        view("/api/v1/jobs?status=MODERATED", adminId, "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].moderationReason").value(REASON));
        view("/api/v1/jobs/" + published.getId(), globexRecruiter, "RECRUITER").andExpect(status().isNotFound());
        view("/api/v1/jobs/" + published.getId(), candidateId, "CANDIDATE").andExpect(status().isNotFound());
    }

    @Test
    void publishedOffersExposeNoModerationReason() throws Exception {
        view("/api/v1/jobs/" + published.getId(), candidateId, "CANDIDATE").andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationReason").value((Object) null));
        view("/api/v1/jobs", candidateId, "CANDIDATE").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].moderationReason").value((Object) null));
    }

    @Test
    void recruiterCannotEditNorReopenAModeratedOffer() throws Exception {
        moderate(published.getId(), REASON).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/jobs/" + published.getId()).with(as(recruiterId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"Contourné\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/jobs/" + published.getId() + "/status").with(as(recruiterId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"PUBLISHED\"}"))
                .andExpect(status().isForbidden());
        assertThat(reload(published.getId()).getStatus()).isEqualTo(JobStatus.MODERATED);
    }

    @Test
    void serviceReadsAnOfferWhateverItsStatus() throws Exception {
        Job closed = saveJob(acme, "Clôturée", JobStatus.CLOSED);

        asService("/internal/v1/jobs/" + closed.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(closed.getId().toString()))
                .andExpect(jsonPath("$.companyId").value(acme.toString()))
                .andExpect(jsonPath("$.title").value("Clôturée"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-01T09:00:00Z"))
                .andExpect(jsonPath("$.description").doesNotExist());
        asService("/internal/v1/jobs/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void statsCountOffersPerStatusGloballyAndPerCompany() throws Exception {
        saveJob(acme, "Brouillon", JobStatus.DRAFT);
        saveJob(acme, "Clôturée", JobStatus.CLOSED);
        saveJob(globex, "Publiée Globex", JobStatus.PUBLISHED);
        saveJob(globex, "Modérée Globex", JobStatus.MODERATED);
        saveJob(globex, "Modérée Globex 2", JobStatus.MODERATED);

        asService("/internal/v1/jobs/stats").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.draft").value(1))
                .andExpect(jsonPath("$.published").value(2))
                .andExpect(jsonPath("$.closed").value(1))
                .andExpect(jsonPath("$.moderated").value(2));
        asService("/internal/v1/jobs/stats?companyId=" + acme).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.draft").value(1))
                .andExpect(jsonPath("$.published").value(1))
                .andExpect(jsonPath("$.closed").value(1))
                .andExpect(jsonPath("$.moderated").value(0));
        asService("/internal/v1/jobs/stats?companyId=" + UUID.randomUUID()).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.published").value(0));
        asService("/internal/v1/jobs/stats?companyId=nope").andExpect(status().isBadRequest());
    }

    @Test
    void emptyDatabaseGivesZeroStats() throws Exception {
        jobRepository.deleteAll();

        asService("/internal/v1/jobs/stats").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.moderated").value(0));
    }

    @Test
    void userRolesAndAnonymousCallersCannotUseInternalEndpoints() throws Exception {
        for (String role : new String[]{"ADMIN", "RECRUITER", "CANDIDATE"}) {
            view("/internal/v1/jobs/" + published.getId(), UUID.randomUUID(), role).andExpect(status().isForbidden());
            view("/internal/v1/jobs/stats", UUID.randomUUID(), role).andExpect(status().isForbidden());
            moderate(published.getId(), "{\"reason\": \"x\"}", UUID.randomUUID(), role).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/internal/v1/jobs/stats")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/internal/v1/jobs/" + published.getId() + "/moderation")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(reload(published.getId()).getStatus()).isEqualTo(JobStatus.PUBLISHED);
    }

    @Test
    void serviceIdentityDoesNotOpenPublicEndpoints() throws Exception {
        view("/api/v1/jobs", UUID.randomUUID(), "SERVICE").andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/jobs/" + published.getId()).with(as(UUID.randomUUID(), "SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"x\"}"))
                .andExpect(status().isForbidden());
    }
}
