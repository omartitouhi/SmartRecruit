package com.smartrecruit.job.controller;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.support.JobApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-JOB-04 — GET /api/v1/jobs: filters, pagination, ordering and visibility per role.
 */
class JobSearchIntegrationTest extends JobApiTest {

    private final UUID candidateId = UUID.randomUUID();
    private final UUID acme = UUID.randomUUID();
    private final UUID globex = UUID.randomUUID();

    private Job javaJob;
    private Job dataJob;
    private Job devopsJob;
    private Job draftJob;
    private Job closedJob;
    private Job moderatedJob;

    @BeforeEach
    void seed() {
        directoryKnows(acme, "Acme");
        javaJob = save(acme, "Développeur Java", "Microservices Spring Boot pour la banque", "Tunis",
                List.of("Java", "Spring"), ContractType.CDI, ExperienceLevel.SENIOR, JobStatus.PUBLISHED, "2026-10-03T09:00:00Z");
        dataJob = save(globex, "Data Engineer", "Pipelines Spark et Kafka", "Sfax",
                List.of("Python", "Kafka", "Spark"), ContractType.CDD, ExperienceLevel.INTERMEDIATE, JobStatus.PUBLISHED, "2026-10-05T09:00:00Z");
        devopsJob = save(acme, "Ingénieur DevOps", "Kubernetes, Terraform et 100% automatisation", "Tunis",
                List.of("Kubernetes", "Java"), ContractType.STAGE, ExperienceLevel.JUNIOR, JobStatus.PUBLISHED, "2026-10-01T09:00:00Z");
        draftJob = save(acme, "Brouillon", "Pas encore publiée", "Tunis",
                List.of("Java"), ContractType.CDI, ExperienceLevel.SENIOR, JobStatus.DRAFT, null);
        closedJob = save(acme, "Offre clôturée", "Plus ouverte", "Tunis",
                List.of("Java"), ContractType.CDI, ExperienceLevel.SENIOR, JobStatus.CLOSED, "2026-10-06T09:00:00Z");
        moderatedJob = save(globex, "Offre retirée", "Retirée par modération", "Tunis",
                List.of("Java"), ContractType.CDI, ExperienceLevel.SENIOR, JobStatus.MODERATED, "2026-10-07T09:00:00Z");
    }

    private Job save(UUID companyId, String title, String description, String location, List<String> skills,
                     ContractType contract, ExperienceLevel level, JobStatus status, String publishedAt) {
        Instant published = publishedAt == null ? null : Instant.parse(publishedAt);
        return jobRepository.save(Job.builder()
                .id(UUID.randomUUID()).companyId(companyId).title(title).description(description)
                .requiredSkills(new ArrayList<>(skills)).experienceLevel(level).contractType(contract)
                .location(location).status(status).publishedAt(published)
                .updatedAt(published == null ? Instant.parse("2026-10-02T09:00:00Z") : published)
                .build());
    }

    private ResultActions search(String query, UUID accountId, String role) throws Exception {
        return mockMvc.perform(get("/api/v1/jobs" + query).with(as(accountId, role)));
    }

    private ResultActions candidateSearch(String query) throws Exception {
        return search(query, candidateId, "CANDIDATE");
    }

    @Test
    void candidatesSeeOnlyPublishedOffersNewestFirstWithCompanyNames() throws Exception {
        candidateSearch("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(dataJob.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(javaJob.getId().toString()))
                .andExpect(jsonPath("$.content[2].id").value(devopsJob.getId().toString()))
                .andExpect(jsonPath("$.content[1].title").value("Développeur Java"))
                .andExpect(jsonPath("$.content[1].companyId").value(acme.toString()))
                .andExpect(jsonPath("$.content[1].companyName").value("Acme"))
                .andExpect(jsonPath("$.content[1].requiredSkills[0]").value("Java"))
                .andExpect(jsonPath("$.content[1].publishedAt").value("2026-10-03T09:00:00Z"))
                .andExpect(jsonPath("$.content[1].updatedAt").value("2026-10-03T09:00:00Z"))
                .andExpect(jsonPath("$.content[1].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content[1].applicationsCount").value((Object) null))
                .andExpect(jsonPath("$.content[0].companyName").value((Object) null));
    }

    @Test
    void keywordMatchesTitleOrDescriptionIgnoringCase() throws Exception {
        candidateSearch("?keyword=java").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(javaJob.getId().toString()));
        candidateSearch("?keyword=KAFKA").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(dataJob.getId().toString()));
        candidateSearch("?keyword=introuvable").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void keywordWildcardsAreMatchedLiterally() throws Exception {
        candidateSearch("?keyword=100%").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(devopsJob.getId().toString()));
        candidateSearch("?keyword=%").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        candidateSearch("?keyword=_").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void locationMatchesIgnoringCase() throws Exception {
        candidateSearch("?location=SFAX").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(dataJob.getId().toString()));
        candidateSearch("?location=tun").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void skillsMatchOffersRequiringAtLeastOneOfThem() throws Exception {
        candidateSearch("?skills=kafka,KUBERNETES").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(dataJob.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(devopsJob.getId().toString()));
        // Two matching skills on the same offer still list it once.
        candidateSearch("?skills=Java,Spring").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        candidateSearch("?skills= ,").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        candidateSearch("?skills=Cobol").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void contractTypeAndExperienceLevelFiltersCombine() throws Exception {
        candidateSearch("?contractType=CDD").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(dataJob.getId().toString()));
        candidateSearch("?experienceLevel=JUNIOR").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(devopsJob.getId().toString()));
        candidateSearch("?contractType=CDI&experienceLevel=SENIOR&location=tunis&skills=java&keyword=banque")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(javaJob.getId().toString()));
        candidateSearch("?contractType=CDD&experienceLevel=SENIOR").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void companyFilterKeepsOnlyThatCompanysPublishedOffers() throws Exception {
        candidateSearch("?companyId=" + acme).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(javaJob.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(devopsJob.getId().toString()));
    }

    @Test
    void paginationSplitsTheResults() throws Exception {
        candidateSearch("?size=2&page=0").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content[0].id").value(dataJob.getId().toString()));
        candidateSearch("?size=2&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content[0].id").value(devopsJob.getId().toString()));
        candidateSearch("?size=2&page=5").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void invalidParametersGet400() throws Exception {
        candidateSearch("?size=51").andExpect(status().isBadRequest());
        candidateSearch("?size=0").andExpect(status().isBadRequest());
        candidateSearch("?page=-1").andExpect(status().isBadRequest());
        candidateSearch("?page=abc").andExpect(status().isBadRequest());
        candidateSearch("?contractType=BENEVOLAT").andExpect(status().isBadRequest());
        candidateSearch("?experienceLevel=GOD").andExpect(status().isBadRequest());
        candidateSearch("?status=ARCHIVED").andExpect(status().isBadRequest());
        candidateSearch("?companyId=not-a-uuid").andExpect(status().isBadRequest());
        candidateSearch("?keyword=" + "k".repeat(101)).andExpect(status().isBadRequest());
    }

    @Test
    void candidatesCannotWidenTheVisibilityWithStatus() throws Exception {
        candidateSearch("?status=CLOSED").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PUBLISHED"))));
        candidateSearch("?status=DRAFT&companyId=" + acme).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void recruitersBrowsingSeeOtherCompaniesPublishedOffersOnly() throws Exception {
        search("?status=DRAFT", UUID.randomUUID(), "RECRUITER").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PUBLISHED"))));
    }

    @Test
    void adminsSeeEveryStatusAndCanFilterByIt() throws Exception {
        search("", UUID.randomUUID(), "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content[0].id").value(moderatedJob.getId().toString()))
                .andExpect(jsonPath("$.content[5].id").value(draftJob.getId().toString()));
        search("?status=CLOSED", UUID.randomUUID(), "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(closedJob.getId().toString()));
        search("?status=MODERATED&companyId=" + globex, UUID.randomUUID(), "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void userServiceOutageStillReturnsTheOffersWithoutCompanyNames() throws Exception {
        BACKENDS.respond("/internal/v1/companies", 500, "");

        candidateSearch("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[1].title").value("Développeur Java"))
                .andExpect(jsonPath("$.content[1].companyName").value((Object) null));
    }

    @Test
    void closingAnOfferRemovesItFromCandidateSearchAndReopeningBringsItBack() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        memberOf(recruiterId, acme, "Acme");

        mockMvc.perform(patch("/api/v1/jobs/" + javaJob.getId() + "/status").with(as(recruiterId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"CLOSED\"}"))
                .andExpect(status().isOk());
        candidateSearch("?keyword=java").andExpect(jsonPath("$.totalElements").value(0));
        search("?keyword=java&status=CLOSED", UUID.randomUUID(), "ADMIN")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(javaJob.getId().toString()));

        mockMvc.perform(patch("/api/v1/jobs/" + javaJob.getId() + "/status").with(as(recruiterId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"PUBLISHED\"}"))
                .andExpect(status().isOk());
        candidateSearch("?keyword=java").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anonymousCallersGet401() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")).andExpect(status().isUnauthorized());
    }
}
