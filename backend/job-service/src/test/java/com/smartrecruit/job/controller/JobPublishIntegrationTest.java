package com.smartrecruit.job.controller;

import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.support.JobApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-JOB-01 — POST /api/v1/jobs. user-service is faked to resolve the recruiter's company.
 */
class JobPublishIntegrationTest extends JobApiTest {

    private static final String VALID_JOB = """
            {"title": "Développeur Java", "description": "Concevoir et maintenir nos microservices Spring Boot.",
             "requiredSkills": ["Java", "Spring Boot"], "experienceLevel": "SENIOR", "contractType": "CDI",
             "location": "Tunis"}
            """;

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void recruiterHasACompany() {
        memberOf(recruiterId, companyId, "Acme");
    }

    private ResultActions publish(String body, UUID accountId, String role) throws Exception {
        return mockMvc.perform(post("/api/v1/jobs").with(as(accountId, role))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void validOfferIsPublishedForTheRecruitersCompany() throws Exception {
        publish(VALID_JOB, recruiterId, "RECRUITER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.title").value("Développeur Java"))
                .andExpect(jsonPath("$.requiredSkills[1]").value("Spring Boot"))
                .andExpect(jsonPath("$.experienceLevel").value("SENIOR"))
                .andExpect(jsonPath("$.contractType").value("CDI"))
                .andExpect(jsonPath("$.location").value("Tunis"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        Job saved = jobRepository.findAll().getFirst();
        assertThat(saved.getStatus()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(saved.getCompanyId()).isEqualTo(companyId);
        assertThat(saved.getPublishedAt()).isNotNull();
        assertThat(BACKENDS.callsTo("/internal/v1/companies/by-member/" + recruiterId)).singleElement().satisfies(call -> {
            assertThat(call.role()).isEqualTo("SERVICE");
            assertThat(call.h2cUpgrade()).as("HTTP/1.1 only").isFalse();
        });
    }

    @Test
    void companyAndStatusSentByTheClientAreIgnored() throws Exception {
        UUID otherCompany = UUID.randomUUID();
        publish("""
                {"companyId": "%s", "status": "CLOSED", "title": "Développeur Java",
                 "description": "Concevoir et maintenir nos microservices Spring Boot.",
                 "requiredSkills": ["Java"], "experienceLevel": "SENIOR", "contractType": "CDI", "location": "Tunis"}
                """.formatted(otherCompany), recruiterId, "RECRUITER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void recruiterWithoutCompanyGets403() throws Exception {
        publish(VALID_JOB, UUID.randomUUID(), "RECRUITER").andExpect(status().isForbidden());
        assertThat(jobRepository.count()).isZero();
    }

    @Test
    void userServiceOutageGets503AndNothingIsStored() throws Exception {
        BACKENDS.respond("/internal/v1/companies/by-member/" + recruiterId, 500, "");

        publish(VALID_JOB, recruiterId, "RECRUITER").andExpect(status().isServiceUnavailable());
        assertThat(jobRepository.count()).isZero();
    }

    @Test
    void missingFieldsGet400WithEveryFieldError() throws Exception {
        publish("{}", recruiterId, "RECRUITER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.title").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.description").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.requiredSkills").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.experienceLevel").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.contractType").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.location").isNotEmpty());
        assertThat(jobRepository.count()).isZero();
    }

    @Test
    void contractLimitsAreEnforced() throws Exception {
        publish("""
                {"title": "%s", "description": "trop court", "requiredSkills": [],
                 "experienceLevel": "SENIOR", "contractType": "CDI", "location": "%s"}
                """.formatted("t".repeat(256), "l".repeat(256)), recruiterId, "RECRUITER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.description").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.requiredSkills").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.location").isNotEmpty());

        String fiftyOneSkills = String.join(",", Collections.nCopies(51, "\"Java\""));
        publish("""
                {"title": "Titre", "description": "%s", "requiredSkills": [%s],
                 "experienceLevel": "SENIOR", "contractType": "CDI", "location": "Tunis"}
                """.formatted("d".repeat(20001), fiftyOneSkills), recruiterId, "RECRUITER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.requiredSkills").isNotEmpty());

        publish("""
                {"title": "Titre", "description": "Une description suffisamment longue",
                 "requiredSkills": ["%s", " "], "experienceLevel": "SENIOR", "contractType": "CDI", "location": "Tunis"}
                """.formatted("s".repeat(101)), recruiterId, "RECRUITER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['requiredSkills[0]']").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors['requiredSkills[1]']").isNotEmpty());
    }

    @Test
    void unknownEnumValueGets400() throws Exception {
        publish(VALID_JOB.replace("CDI", "BENEVOLAT"), recruiterId, "RECRUITER")
                .andExpect(status().isBadRequest());
    }

    @Test
    void htmlInFreeTextIsStrippedBeforeStorage() throws Exception {
        publish("""
                {"title": "Dev <b>Java</b>", "description": "Concevoir nos services <script>alert(1)</script>Spring Boot.",
                 "requiredSkills": ["<i>Java</i>"], "experienceLevel": "SENIOR", "contractType": "CDI", "location": "Tunis"}
                """, recruiterId, "RECRUITER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Dev Java"))
                .andExpect(jsonPath("$.description").value("Concevoir nos services Spring Boot."))
                .andExpect(jsonPath("$.requiredSkills[0]").value("Java"));
    }

    @Test
    void onlyRecruitersCanPublish() throws Exception {
        publish(VALID_JOB, UUID.randomUUID(), "CANDIDATE").andExpect(status().isForbidden());
        publish(VALID_JOB, UUID.randomUUID(), "ADMIN").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_JOB))
                .andExpect(status().isUnauthorized());
        assertThat(jobRepository.count()).isZero();
    }
}
