package com.smartrecruit.job.controller;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.support.JobApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-JOB-02 — PATCH /api/v1/jobs/{id} (JSON merge patch).
 */
class JobUpdateIntegrationTest extends JobApiTest {

    private static final String ANALYZE = "/api/v1/ai/job-analysis/analyze";

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private Job job;

    @BeforeEach
    void recruiterOwnsAPublishedJob() {
        memberOf(recruiterId, companyId, "Acme");
        job = saveJob(companyId, "Développeur Java", JobStatus.PUBLISHED);
    }

    private ResultActions patchJob(UUID id, String body, UUID accountId, String role) throws Exception {
        return mockMvc.perform(patch("/api/v1/jobs/" + id).with(as(accountId, role))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patchJob(String body) throws Exception {
        return patchJob(job.getId(), body, recruiterId, "RECRUITER");
    }

    private Job reload() {
        return reload(job.getId());
    }

    @Test
    void onlyTheSentFieldsChangeAndTheEditIsTimestamped() throws Exception {
        patchJob("{\"title\": \"Lead Java\", \"contractType\": \"CDD\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(job.getId().toString()))
                .andExpect(jsonPath("$.title").value("Lead Java"))
                .andExpect(jsonPath("$.contractType").value("CDD"))
                .andExpect(jsonPath("$.location").value("Tunis"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value("2026-10-01T09:00:00Z"));

        Job saved = reload();
        assertThat(saved.getTitle()).isEqualTo("Lead Java");
        assertThat(saved.getContractType()).isEqualTo(ContractType.CDD);
        assertThat(saved.getRequiredSkills()).containsExactly("Java", "Spring");
        assertThat(saved.getDescription()).isEqualTo(job.getDescription());
        assertThat(saved.getUpdatedAt()).isAfter(Instant.parse("2026-10-01T09:00:00Z"));
        assertThat(saved.getPublishedAt()).isEqualTo(Instant.parse("2026-10-01T09:00:00Z"));
    }

    @Test
    void allEditableFieldsCanBeReplaced() throws Exception {
        patchJob("""
                {"title": "Architecte", "description": "Piloter l'architecture de nos services.",
                 "requiredSkills": ["Kafka", "Kubernetes"], "experienceLevel": "LEAD", "contractType": "FREELANCE",
                 "location": "Sfax"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredSkills.length()").value(2))
                .andExpect(jsonPath("$.requiredSkills[0]").value("Kafka"))
                .andExpect(jsonPath("$.experienceLevel").value("LEAD"))
                .andExpect(jsonPath("$.location").value("Sfax"));

        Job saved = reload();
        assertThat(saved.getRequiredSkills()).containsExactly("Kafka", "Kubernetes");
        assertThat(saved.getDescription()).isEqualTo("Piloter l'architecture de nos services.");
    }

    @Test
    void statusCompanyAndIdInTheBodyAreNotEditable() throws Exception {
        patchJob("{\"status\": \"CLOSED\", \"companyId\": \"%s\", \"id\": \"%s\", \"publishedAt\": null, \"location\": \"Sfax\"}"
                .formatted(UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.id").value(job.getId().toString()))
                .andExpect(jsonPath("$.location").value("Sfax"));
    }

    @Test
    void changingTitleDescriptionOrSkillsAsksTheAiForANewAnalysis() throws Exception {
        patchJob("{\"title\": \"Lead Java\"}").andExpect(status().isOk());
        patchJob("{\"requiredSkills\": [\"Kafka\"]}").andExpect(status().isOk());

        assertThat(BACKENDS.awaitCallsTo(ANALYZE, 2)).hasSize(2).allSatisfy(call -> {
            assertThat(call.role()).isEqualTo("SERVICE");
            assertThat(call.body()).contains("\"jobId\":\"" + job.getId() + "\"");
        });
        // The two analyses run asynchronously: their arrival order is not guaranteed.
        assertThat(BACKENDS.callsTo(ANALYZE)).anySatisfy(call -> assertThat(call.body()).contains("\"title\":\"Lead Java\""));
    }

    @Test
    void otherChangesOrIdenticalValuesDoNotTriggerAnalysisNorTouchUpdatedAt() throws Exception {
        patchJob("{\"location\": \"Sfax\", \"experienceLevel\": \"JUNIOR\"}").andExpect(status().isOk());
        Instant afterDetails = reload().getUpdatedAt();
        assertThat(afterDetails).isAfter(Instant.parse("2026-10-01T09:00:00Z"));

        patchJob("{\"title\": \"Développeur Java\", \"requiredSkills\": [\"Java\", \"Spring\"], \"location\": \"Sfax\"}")
                .andExpect(status().isOk());
        patchJob("{}").andExpect(status().isOk());

        assertThat(reload().getUpdatedAt()).isEqualTo(afterDetails);
        // A later real change is the only analysis request ever made.
        patchJob("{\"title\": \"Nouveau titre\"}").andExpect(status().isOk());
        assertThat(BACKENDS.awaitCallsTo(ANALYZE, 1)).hasSize(1);
        assertThat(BACKENDS.callsTo(ANALYZE).getFirst().body()).contains("Nouveau titre");
    }

    @Test
    void aiFailureNeverAffectsTheEdit() throws Exception {
        BACKENDS.respond(ANALYZE, 500, "");

        patchJob("{\"title\": \"Lead Java\"}").andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Lead Java"));

        BACKENDS.awaitCallsTo(ANALYZE, 1);
        assertThat(reload().getTitle()).isEqualTo("Lead Java");
    }

    @Test
    void draftOffersCanBeEdited() throws Exception {
        Job draft = saveJob(companyId, "Brouillon", JobStatus.DRAFT);

        patchJob(draft.getId(), "{\"title\": \"Brouillon revu\"}", recruiterId, "RECRUITER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void offerOfAnotherCompanyGets403AndStaysUntouched() throws Exception {
        UUID otherRecruiter = UUID.randomUUID();
        memberOf(otherRecruiter, UUID.randomUUID(), "Globex");

        patchJob(job.getId(), "{\"title\": \"Piraté\"}", otherRecruiter, "RECRUITER").andExpect(status().isForbidden());

        assertThat(reload().getTitle()).isEqualTo("Développeur Java");
        assertThat(BACKENDS.callsTo(ANALYZE)).isEmpty();
    }

    @Test
    void recruiterWithoutCompanyGets403() throws Exception {
        patchJob(job.getId(), "{\"title\": \"X\"}", UUID.randomUUID(), "RECRUITER").andExpect(status().isForbidden());
    }

    @Test
    void unknownOfferGets404() throws Exception {
        patchJob(UUID.randomUUID(), "{\"title\": \"X\"}", recruiterId, "RECRUITER").andExpect(status().isNotFound());
    }

    @Test
    void closedAndModeratedOffersGet409() throws Exception {
        Job closed = saveJob(companyId, "Clôturée", JobStatus.CLOSED);
        Job moderated = saveJob(companyId, "Modérée", JobStatus.MODERATED);

        patchJob(closed.getId(), "{\"title\": \"X\"}", recruiterId, "RECRUITER").andExpect(status().isConflict());
        patchJob(moderated.getId(), "{\"title\": \"X\"}", recruiterId, "RECRUITER").andExpect(status().isConflict());

        assertThat(jobRepository.findById(closed.getId()).orElseThrow().getTitle()).isEqualTo("Clôturée");
    }

    @Test
    void userServiceOutageGets503() throws Exception {
        BACKENDS.respond("/internal/v1/companies/by-member/" + recruiterId, 500, "");

        patchJob("{\"title\": \"Lead Java\"}").andExpect(status().isServiceUnavailable());
        assertThat(reload().getTitle()).isEqualTo("Développeur Java");
    }

    @Test
    void mandatoryFieldsCannotBeRemoved() throws Exception {
        patchJob("{\"title\": null, \"requiredSkills\": null, \"contractType\": null, \"location\": null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.requiredSkills").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.contractType").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.location").isNotEmpty());
        assertThat(reload().getTitle()).isEqualTo("Développeur Java");
    }

    @Test
    void invalidValuesGet400WithFieldErrorsAndNothingIsApplied() throws Exception {
        String fiftyOneSkills = String.join(",", Collections.nCopies(51, "\"Java\""));
        patchJob("{\"title\": \"  \", \"description\": \"trop court\", \"location\": \"%s\", \"requiredSkills\": [%s]}"
                .formatted("l".repeat(256), fiftyOneSkills))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.description").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.location").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.requiredSkills").isNotEmpty());

        patchJob("{\"requiredSkills\": [\" \", \"%s\"]}".formatted("s".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['requiredSkills[0]']").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors['requiredSkills[1]']").isNotEmpty());

        Job saved = reload();
        assertThat(saved.getTitle()).isEqualTo("Développeur Java");
        assertThat(saved.getRequiredSkills()).containsExactly("Java", "Spring");
    }

    @Test
    void malformedBodiesGet400() throws Exception {
        patchJob("{\"experienceLevel\": \"GOD\"}").andExpect(status().isBadRequest());
        patchJob("{\"requiredSkills\": \"Java\"}").andExpect(status().isBadRequest());
        patchJob("[]").andExpect(status().isBadRequest());
        patchJob("{\"title\": ").andExpect(status().isBadRequest());
    }

    @Test
    void htmlInPatchedTextIsStripped() throws Exception {
        patchJob("{\"title\": \"Lead <b>Java</b><script>alert(1)</script>\", \"requiredSkills\": [\"<i>Kafka</i>\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lead Java"))
                .andExpect(jsonPath("$.requiredSkills[0]").value("Kafka"));
        assertThat(reload().getRequiredSkills()).containsExactly("Kafka");
    }

    @Test
    void onlyRecruitersCanEdit() throws Exception {
        patchJob(job.getId(), "{\"title\": \"X\"}", UUID.randomUUID(), "CANDIDATE").andExpect(status().isForbidden());
        patchJob(job.getId(), "{\"title\": \"X\"}", UUID.randomUUID(), "ADMIN").andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/jobs/" + job.getId()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
