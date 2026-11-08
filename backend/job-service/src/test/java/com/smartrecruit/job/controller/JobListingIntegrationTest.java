package com.smartrecruit.job.controller;

import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.support.JobApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-JOB-05 — GET /api/v1/jobs?companyId=me and GET /api/v1/jobs/{id}.
 */
class JobListingIntegrationTest extends JobApiTest {

    private static final String COUNTS = "/internal/v1/applications/counts";

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID acme = UUID.randomUUID();
    private final UUID globex = UUID.randomUUID();

    private Job published;
    private Job draft;
    private Job closed;
    private Job moderated;
    private Job foreign;

    @BeforeEach
    void seed() {
        memberOf(recruiterId, acme, "Acme");
        directoryKnows(acme, "Acme");
        published = saveJob(acme, "Publiée", JobStatus.PUBLISHED);
        draft = saveJob(acme, "Brouillon", JobStatus.DRAFT);
        closed = saveJob(acme, "Clôturée", JobStatus.CLOSED);
        moderated = saveJob(acme, "Modérée", JobStatus.MODERATED);
        foreign = saveJob(globex, "Chez Globex", JobStatus.PUBLISHED);
        BACKENDS.respond(COUNTS, 200, "{\"%s\": 4, \"%s\": 1}".formatted(published.getId(), closed.getId()));
    }

    private ResultActions call(String path, UUID accountId, String role) throws Exception {
        return mockMvc.perform(get(path).with(as(accountId, role)));
    }

    private ResultActions mine(String query) throws Exception {
        return call("/api/v1/jobs?companyId=me" + query, recruiterId, "RECRUITER");
    }

    @Test
    void recruiterListsEveryStatusOfOwnOffersWithApplicationCounts() throws Exception {
        mine("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].applicationsCount".formatted(published.getId())).value(4))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].applicationsCount".formatted(closed.getId())).value(1))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].applicationsCount".formatted(draft.getId())).value(0))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].applicationsCount".formatted(moderated.getId())).value(0))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].status".formatted(draft.getId())).value("DRAFT"))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].companyName".formatted(published.getId())).value("Acme"))
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(foreign.getId())).isEmpty());
        // Drafts have no publication date and come last.
        mine("").andExpect(jsonPath("$.content[3].id").value(draft.getId().toString()));

        assertThat(BACKENDS.callsTo(COUNTS)).first().satisfies(call -> {
            assertThat(call.role()).isEqualTo("SERVICE");
            assertThat(call.h2cUpgrade()).isFalse();
            assertThat(call.query()).startsWith("jobIds=").contains(published.getId().toString(), draft.getId().toString())
                    .doesNotContain(foreign.getId().toString());
        });
    }

    @Test
    void listingCombinesWithStatusAndOtherFilters() throws Exception {
        mine("&status=CLOSED").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(closed.getId().toString()))
                .andExpect(jsonPath("$.content[0].applicationsCount").value(1));
        mine("&keyword=brouillon").andExpect(jsonPath("$.totalElements").value(1));
        mine("&size=2&page=1").andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void applicationServiceOutageShowsZeroApplications() throws Exception {
        BACKENDS.respond(COUNTS, 500, "");

        mine("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].applicationsCount".formatted(published.getId())).value(0));
    }

    @Test
    void userServiceOutageOnTheDirectoryOnlyDropsCompanyNames() throws Exception {
        BACKENDS.respond("/internal/v1/companies", 500, "");

        mine("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].companyName".formatted(published.getId())).value((Object) null))
                .andExpect(jsonPath("$.content[?(@.id == '%s')].applicationsCount".formatted(published.getId())).value(4));
    }

    @Test
    void unknownCompanyOfTheRecruiterGivesAnEmptyPage() throws Exception {
        call("/api/v1/jobs?companyId=me", UUID.randomUUID(), "RECRUITER").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void userServiceOutageOnTheMembershipLookupGets503() throws Exception {
        BACKENDS.respond("/internal/v1/companies/by-member/" + recruiterId, 500, "");

        mine("").andExpect(status().isServiceUnavailable());
    }

    @Test
    void onlyRecruitersCanUseCompanyIdMe() throws Exception {
        call("/api/v1/jobs?companyId=me", UUID.randomUUID(), "CANDIDATE").andExpect(status().isForbidden());
        call("/api/v1/jobs?companyId=me", UUID.randomUUID(), "ADMIN").andExpect(status().isForbidden());
    }

    @Test
    void applicationCountsAreOnlyRequestedForTheOwnListing() throws Exception {
        call("/api/v1/jobs", UUID.randomUUID(), "CANDIDATE").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].applicationsCount").value((Object) null));
        call("/api/v1/jobs?companyId=" + acme, recruiterId, "RECRUITER").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        assertThat(BACKENDS.callsTo(COUNTS)).isEmpty();
    }

    @Test
    void anyoneSeesAPublishedOfferWithItsCompanyName() throws Exception {
        for (String role : new String[]{"CANDIDATE", "RECRUITER", "ADMIN"}) {
            call("/api/v1/jobs/" + published.getId(), UUID.randomUUID(), role)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(published.getId().toString()))
                    .andExpect(jsonPath("$.title").value("Publiée"))
                    .andExpect(jsonPath("$.description").value(published.getDescription()))
                    .andExpect(jsonPath("$.requiredSkills.length()").value(2))
                    .andExpect(jsonPath("$.companyId").value(acme.toString()))
                    .andExpect(jsonPath("$.companyName").value("Acme"))
                    .andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.updatedAt").value("2026-10-01T09:00:00Z"))
                    .andExpect(jsonPath("$.applicationsCount").value((Object) null));
        }
    }

    @Test
    void candidatesGet404ForUnpublishedOffers() throws Exception {
        for (Job job : new Job[]{draft, closed, moderated}) {
            call("/api/v1/jobs/" + job.getId(), UUID.randomUUID(), "CANDIDATE").andExpect(status().isNotFound());
        }
    }

    @Test
    void ownerAndAdminSeeEveryStatusOtherCompaniesDoNot() throws Exception {
        UUID globexRecruiter = UUID.randomUUID();
        memberOf(globexRecruiter, globex, "Globex");

        for (Job job : new Job[]{draft, closed, moderated}) {
            call("/api/v1/jobs/" + job.getId(), recruiterId, "RECRUITER").andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(job.getStatus().name()));
            call("/api/v1/jobs/" + job.getId(), UUID.randomUUID(), "ADMIN").andExpect(status().isOk());
            call("/api/v1/jobs/" + job.getId(), globexRecruiter, "RECRUITER").andExpect(status().isNotFound());
        }
    }

    @Test
    void unknownOrMalformedIdentifiers() throws Exception {
        call("/api/v1/jobs/" + UUID.randomUUID(), UUID.randomUUID(), "ADMIN").andExpect(status().isNotFound());
        call("/api/v1/jobs/not-a-uuid", UUID.randomUUID(), "ADMIN").andExpect(status().isBadRequest());
    }

    @Test
    void userServiceOutageDoesNotBreakAPublishedOfferButBlocksOwnershipChecks() throws Exception {
        BACKENDS.respond("/internal/v1/companies", 500, "");
        BACKENDS.respond("/internal/v1/companies/by-member/" + recruiterId, 500, "");

        call("/api/v1/jobs/" + published.getId(), recruiterId, "RECRUITER").andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value((Object) null));
        call("/api/v1/jobs/" + closed.getId(), recruiterId, "RECRUITER").andExpect(status().isServiceUnavailable());
        call("/api/v1/jobs/" + closed.getId(), UUID.randomUUID(), "ADMIN").andExpect(status().isOk());
    }

    @Test
    void recommendationsKeepPriorityOverTheIdMapping() throws Exception {
        call("/api/v1/jobs/recommendations", UUID.randomUUID(), "CANDIDATE")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Les recommandations sont temporairement indisponibles, réessayez plus tard"));
        call("/api/v1/jobs/recommendations", UUID.randomUUID(), "RECRUITER").andExpect(status().isForbidden());
        assertThat(BACKENDS.callsTo("/api/v1/ai/recommendation/candidates/x/jobs")).isEmpty();
    }

    @Test
    void anonymousCallersGet401() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/" + published.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/jobs?companyId=me")).andExpect(status().isUnauthorized());
    }
}
