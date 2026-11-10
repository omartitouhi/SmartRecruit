package com.smartrecruit.application.controller;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationNote;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** EXT-APP-03 — team notes and ratings, never visible to the candidate. */
class NotesIntegrationTest extends ApiTestSupport {

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID colleagueId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private Application application;

    @BeforeEach
    void setUp() {
        stubCompanyOf(recruiterId, companyId);
        stubCompanyOf(colleagueId, companyId);
        stubJob(jobId, companyId, "PUBLISHED", Instant.now());
        application = applications.save(Application.builder().id(UUID.randomUUID()).jobId(jobId)
                .candidateId(UUID.randomUUID()).companyId(companyId).status(ApplicationStatus.IN_REVIEW)
                .appliedAt(Instant.now()).build());
    }

    private ResultActions addNote(UUID caller, String role, String body) throws Exception {
        return mockMvc.perform(as(post("/api/v1/applications/" + application.getId() + "/notes"), caller, role)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions listNotes(UUID caller, String role) throws Exception {
        return mockMvc.perform(as(get("/api/v1/applications/" + application.getId() + "/notes"), caller, role));
    }

    private ApplicationNote note(UUID author, String content, Integer rating, Instant createdAt) {
        return notes.save(ApplicationNote.builder().id(UUID.randomUUID()).applicationId(application.getId())
                .authorId(author).content(content).rating(rating).createdAt(createdAt).build());
    }

    @Test
    void memberAddsANoteWithRating() throws Exception {
        addNote(recruiterId, "RECRUITER", "{\"content\":\"Très bon échange technique\",\"rating\":4}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.applicationId").value(application.getId().toString()))
                .andExpect(jsonPath("$.authorId").value(recruiterId.toString()))
                .andExpect(jsonPath("$.content").value("Très bon échange technique"))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.createdAt").exists());

        assertThat(notes.count()).isEqualTo(1);
    }

    @Test
    void ratingIsOptionalAndBounded() throws Exception {
        addNote(recruiterId, "RECRUITER", "{\"content\":\"À recontacter\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(nullValue()));
        addNote(recruiterId, "RECRUITER", "{\"content\":\"x\",\"rating\":6}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").exists());
        addNote(recruiterId, "RECRUITER", "{\"content\":\"x\",\"rating\":0}").andExpect(status().isBadRequest());
        addNote(recruiterId, "RECRUITER", "{\"content\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
        addNote(recruiterId, "RECRUITER", "{\"content\":\"%s\"}".formatted("n".repeat(2001))).andExpect(status().isBadRequest());
        assertThat(notes.count()).isEqualTo(1);
    }

    @Test
    void listIsOldestFirstForMembersAndAdmins() throws Exception {
        note(colleagueId, "Deuxième", 2, Instant.parse("2026-02-02T10:00:00Z"));
        note(recruiterId, "Première", 5, Instant.parse("2026-02-01T10:00:00Z"));

        listNotes(recruiterId, "RECRUITER").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("Première"))
                .andExpect(jsonPath("$[1].content").value("Deuxième"))
                .andExpect(jsonPath("$[1].authorId").value(colleagueId.toString()));
        listNotes(UUID.randomUUID(), "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void candidateNeverReadsOrWritesNotes() throws Exception {
        listNotes(application.getCandidateId(), "CANDIDATE").andExpect(status().isForbidden());
        addNote(application.getCandidateId(), "CANDIDATE", "{\"content\":\"Bonjour\"}").andExpect(status().isForbidden());
    }

    @Test
    void adminCannotWriteNotes() throws Exception {
        addNote(UUID.randomUUID(), "ADMIN", "{\"content\":\"Note\"}").andExpect(status().isForbidden());
    }

    @Test
    void recruiterOfAnotherCompanyGets403() throws Exception {
        UUID stranger = UUID.randomUUID();
        stubCompanyOf(stranger, UUID.randomUUID());

        listNotes(stranger, "RECRUITER").andExpect(status().isForbidden());
        addNote(stranger, "RECRUITER", "{\"content\":\"Note\"}").andExpect(status().isForbidden());
        assertThat(notes.count()).isZero();
    }

    @Test
    void unknownApplicationGets404() throws Exception {
        mockMvc.perform(as(get("/api/v1/applications/" + UUID.randomUUID() + "/notes"), recruiterId, "RECRUITER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheAuthorDeletesANote() throws Exception {
        ApplicationNote mine = note(recruiterId, "Ma note", null, Instant.now());

        mockMvc.perform(as(delete("/api/v1/applications/" + application.getId() + "/notes/" + mine.getId()), colleagueId, "RECRUITER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(delete("/api/v1/applications/" + application.getId() + "/notes/" + mine.getId()), recruiterId, "RECRUITER"))
                .andExpect(status().isNoContent());
        assertThat(notes.count()).isZero();
        mockMvc.perform(as(delete("/api/v1/applications/" + application.getId() + "/notes/" + mine.getId()), recruiterId, "RECRUITER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheAuthorEditsANote() throws Exception {
        ApplicationNote mine = note(recruiterId, "Ma note", 2, Instant.now());
        String url = "/api/v1/applications/" + application.getId() + "/notes/" + mine.getId();
        String body = "{\"content\": \"  Entretien très positif \", \"rating\": 5}";

        mockMvc.perform(as(put(url), colleagueId, "RECRUITER").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put(url), recruiterId, "RECRUITER").contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(put(url), recruiterId, "RECRUITER").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Entretien très positif"))
                .andExpect(jsonPath("$.rating").value(5));
        assertThat(notes.findById(mine.getId())).get().satisfies(saved -> {
            assertThat(saved.getContent()).isEqualTo("Entretien très positif");
            assertThat(saved.getRating()).isEqualTo(5);
        });
        mockMvc.perform(as(put("/api/v1/applications/" + application.getId() + "/notes/" + UUID.randomUUID()), recruiterId, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void noteOfAnotherApplicationGets404() throws Exception {
        ApplicationNote mine = note(recruiterId, "Ma note", null, Instant.now());

        mockMvc.perform(as(delete("/api/v1/applications/" + UUID.randomUUID() + "/notes/" + mine.getId()), recruiterId, "RECRUITER"))
                .andExpect(status().isNotFound());
        assertThat(notes.count()).isEqualTo(1);
    }

    @Test
    void recruiterListCarriesNotesCountAndAverageOfRatedNotes() throws Exception {
        note(recruiterId, "a", 4, Instant.now());
        note(colleagueId, "b", 5, Instant.now());
        note(colleagueId, "c", 3, Instant.now());
        note(colleagueId, "sans note", null, Instant.now());
        Application other = applications.save(Application.builder().id(UUID.randomUUID()).jobId(jobId)
                .candidateId(UUID.randomUUID()).companyId(companyId).status(ApplicationStatus.RECEIVED)
                .appliedAt(Instant.now()).build());

        mockMvc.perform(as(get("/api/v1/applications").param("jobId", jobId.toString()), recruiterId, "RECRUITER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].notesCount".formatted(application.getId())).value(4))
                .andExpect(jsonPath("$[?(@.id == '%s')].averageRating".formatted(application.getId())).value(4.0))
                .andExpect(jsonPath("$[?(@.id == '%s')].notesCount".formatted(other.getId())).value(0));
    }

    @Test
    void averageIsRoundedToOneDecimal() throws Exception {
        note(recruiterId, "a", 4, Instant.now());
        note(recruiterId, "b", 4, Instant.now());
        note(recruiterId, "c", 5, Instant.now());

        mockMvc.perform(as(get("/api/v1/applications").param("jobId", jobId.toString()), recruiterId, "RECRUITER"))
                .andExpect(jsonPath("$[0].averageRating").value(4.3));
    }

    @Test
    void candidateViewHasNoNotesSummary() throws Exception {
        note(recruiterId, "Confidentiel", 1, Instant.now());

        mockMvc.perform(as(get("/api/v1/applications/me"), application.getCandidateId(), "CANDIDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notesCount").value(nullValue()))
                .andExpect(jsonPath("$[0].averageRating").value(nullValue()));
    }
}
