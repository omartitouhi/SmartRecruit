package com.smartrecruit.application.controller;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationNote;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DELETE /internal/v1/candidates/{id}/data — right to be forgotten for cover letters and team notes (EXT-APP-01/03). */
class InternalCandidateDataIntegrationTest extends ApiTestSupport {

    private final UUID service = new UUID(0, 0);
    private final UUID candidateId = UUID.randomUUID();

    private Application save(UUID candidate, String coverLetter) {
        return applications.save(Application.builder().id(UUID.randomUUID()).jobId(UUID.randomUUID()).candidateId(candidate)
                .companyId(UUID.randomUUID()).status(ApplicationStatus.IN_REVIEW).appliedAt(Instant.now())
                .coverLetter(coverLetter).build());
    }

    private void note(Application application) {
        notes.save(ApplicationNote.builder().id(UUID.randomUUID()).applicationId(application.getId())
                .authorId(UUID.randomUUID()).content("Avis").rating(3).createdAt(Instant.now()).build());
    }

    @Test
    void erasesCoverLettersAndNotesOfTheCandidateOnly() throws Exception {
        Application mine = save(candidateId, "Ma lettre");
        Application mineToo = save(candidateId, null);
        Application other = save(UUID.randomUUID(), "Autre lettre");
        note(mine);
        note(mineToo);
        note(other);

        mockMvc.perform(as(delete("/internal/v1/candidates/" + candidateId + "/data"), service, "SERVICE"))
                .andExpect(status().isNoContent());

        assertThat(applications.findById(mine.getId()).orElseThrow().getCoverLetter()).isNull();
        assertThat(applications.findById(mine.getId()).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.IN_REVIEW);
        assertThat(applications.findById(other.getId()).orElseThrow().getCoverLetter()).isEqualTo("Autre lettre");
        assertThat(notes.findAll()).singleElement().satisfies(note -> assertThat(note.getApplicationId()).isEqualTo(other.getId()));
    }

    @Test
    void isIdempotent() throws Exception {
        mockMvc.perform(as(delete("/internal/v1/candidates/" + candidateId + "/data"), service, "SERVICE"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(delete("/internal/v1/candidates/" + candidateId + "/data"), service, "SERVICE"))
                .andExpect(status().isNoContent());
    }

    @Test
    void userRolesGet403() throws Exception {
        save(candidateId, "Ma lettre");
        for (String role : new String[]{"CANDIDATE", "RECRUITER", "ADMIN"}) {
            mockMvc.perform(as(delete("/internal/v1/candidates/" + candidateId + "/data"), candidateId, role))
                    .andExpect(status().isForbidden());
        }
        assertThat(applications.findAll().getFirst().getCoverLetter()).isEqualTo("Ma lettre");
    }
}
