package com.smartrecruit.user.controller;

import com.smartrecruit.user.repository.CandidateProfileRepository;
import com.smartrecruit.user.repository.DeletedCandidateRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import com.smartrecruit.user.storage.CvNotFoundException;
import com.smartrecruit.user.storage.CvStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.smartrecruit.user.controller.CandidateCvIntegrationTest.PDF;
import static com.smartrecruit.user.controller.CandidateProfileCreateIntegrationTest.FULL_PROFILE;
import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-USER-04 — DELETE /api/v1/users/candidates/me and the "compte supprimé" view.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CandidateAccountDeletionIntegrationTest {

    private static final String ME = "/api/v1/users/candidates/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CandidateProfileRepository profileRepository;

    @Autowired
    private DeletedCandidateRepository deletedCandidateRepository;

    @Autowired
    private CvStorage cvStorage;

    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        profileRepository.deleteAll();
        deletedCandidateRepository.deleteAll();
        mockMvc.perform(put(ME).with(as(verifier, candidateId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(FULL_PROFILE))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart(ME + "/cv")
                        .file(new MockMultipartFile("file", "cv.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                        .with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isOk());
    }

    private ResultActions deleteMe(String query) throws Exception {
        return mockMvc.perform(delete(ME + query).with(as(verifier, candidateId, "CANDIDATE")));
    }

    @Test
    void confirmedDeletionRemovesProfileAndCvImmediately() throws Exception {
        String cvKey = profileRepository.findById(candidateId).orElseThrow().getCvStorageKey();

        deleteMe("?confirm=true").andExpect(status().isNoContent());

        assertThat(profileRepository.findById(candidateId)).isEmpty();
        assertThatThrownBy(() -> cvStorage.load(cvKey)).isInstanceOf(CvNotFoundException.class);
        assertThat(deletedCandidateRepository.existsById(candidateId)).isTrue();
        mockMvc.perform(get(ME).with(as(verifier, candidateId, "CANDIDATE"))).andExpect(status().isNotFound());
    }

    @Test
    void deletionWithoutConfirmationIsRejected() throws Exception {
        deleteMe("").andExpect(status().isBadRequest());
        deleteMe("?confirm=false").andExpect(status().isBadRequest());

        assertThat(profileRepository.findById(candidateId)).isPresent();
    }

    @Test
    void recruiterSeesDeletedAccountWithoutPersonalData() throws Exception {
        deleteMe("?confirm=true").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/candidates/{id}", candidateId).with(as(verifier, UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(candidateId.toString()))
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.firstName").doesNotExist())
                .andExpect(jsonPath("$.lastName").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.cvUrl").doesNotExist())
                .andExpect(jsonPath("$.skills").doesNotExist());
        mockMvc.perform(get("/api/v1/users/candidates/{id}/cv", candidateId).with(as(verifier, UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void recruiterSeesActiveProfileNormally() throws Exception {
        mockMvc.perform(get("/api/v1/users/candidates/{id}", candidateId).with(as(verifier, UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(false))
                .andExpect(jsonPath("$.lastName").value("Martin"));
        mockMvc.perform(get("/api/v1/users/candidates/{id}", UUID.randomUUID()).with(as(verifier, UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void candidatesCannotUseTheRecruiterView() throws Exception {
        mockMvc.perform(get("/api/v1/users/candidates/{id}", candidateId).with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedAccountCannotRecreateData() throws Exception {
        deleteMe("?confirm=true").andExpect(status().isNoContent());

        mockMvc.perform(put(ME).with(as(verifier, candidateId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(FULL_PROFILE))
                .andExpect(status().isGone());
        mockMvc.perform(multipart(ME + "/cv")
                        .file(new MockMultipartFile("file", "cv.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                        .with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isGone());
        assertThat(profileRepository.findById(candidateId)).isEmpty();
    }

    @Test
    void deletionIsIdempotentAndWorksWithoutProfile() throws Exception {
        deleteMe("?confirm=true").andExpect(status().isNoContent());
        deleteMe("?confirm=true").andExpect(status().isNoContent());

        UUID withoutProfile = UUID.randomUUID();
        mockMvc.perform(delete(ME + "?confirm=true").with(as(verifier, withoutProfile, "CANDIDATE")))
                .andExpect(status().isNoContent());
        assertThat(deletedCandidateRepository.existsById(withoutProfile)).isTrue();
    }

    @Test
    void onlyCandidatesCanDeleteTheirAccountHere() throws Exception {
        mockMvc.perform(delete(ME + "?confirm=true").with(as(verifier, candidateId, "RECRUITER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ME + "?confirm=true")).andExpect(status().isUnauthorized());
    }
}
