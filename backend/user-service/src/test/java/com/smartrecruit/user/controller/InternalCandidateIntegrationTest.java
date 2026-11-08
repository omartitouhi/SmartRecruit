package com.smartrecruit.user.controller;

import com.smartrecruit.user.model.CandidateProfile;
import com.smartrecruit.user.model.CvAnalysisStatus;
import com.smartrecruit.user.repository.CandidateProfileRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /internal/v1/candidates/{id}/readiness — used by application-service before an application.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalCandidateIntegrationTest {

    private static final UUID SERVICE_ID = new UUID(0, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CandidateProfileRepository profileRepository;

    private UUID saveProfile(String firstName, String lastName, String email, String cvKey,
                             CvAnalysisStatus status, Instant parsedAt) {
        return profileRepository.save(CandidateProfile.builder().id(UUID.randomUUID())
                .firstName(firstName).lastName(lastName).email(email)
                .cvStorageKey(cvKey).cvAnalysisStatus(status).cvParsedAt(parsedAt).build()).getId();
    }

    private org.springframework.test.web.servlet.ResultActions readiness(UUID id) throws Exception {
        return mockMvc.perform(get("/internal/v1/candidates/" + id + "/readiness")
                .with(as(verifier, SERVICE_ID, "SERVICE")));
    }

    @Test
    void completeProfileWithAnalysedCv() throws Exception {
        Instant parsedAt = Instant.parse("2026-03-01T10:00:00Z");
        UUID id = saveProfile("Alice", "Martin", "alice@example.com", "key", CvAnalysisStatus.DONE, parsedAt);

        readiness(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.profileComplete").value(true))
                .andExpect(jsonPath("$.hasCv").value(true))
                .andExpect(jsonPath("$.cvAnalysisStatus").value("DONE"))
                .andExpect(jsonPath("$.cvParsedAt").value("2026-03-01T10:00:00Z"));
    }

    @Test
    void profileWithoutCvIsNotReadyToApply() throws Exception {
        UUID id = saveProfile("Alice", "Martin", "alice@example.com", null, null, null);

        readiness(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.profileComplete").value(true))
                .andExpect(jsonPath("$.hasCv").value(false))
                .andExpect(jsonPath("$.cvAnalysisStatus").value("NONE"))
                .andExpect(jsonPath("$.cvParsedAt").doesNotExist());
    }

    @Test
    void cvBeingAnalysedOrFailedStillCountsAsACv() throws Exception {
        readiness(saveProfile("A", "B", "a@b.test", "key", CvAnalysisStatus.PENDING, null))
                .andExpect(jsonPath("$.hasCv").value(true))
                .andExpect(jsonPath("$.cvAnalysisStatus").value("PENDING"));
        readiness(saveProfile("A", "B", "a@b.test", "key", CvAnalysisStatus.FAILED, null))
                .andExpect(jsonPath("$.hasCv").value(true))
                .andExpect(jsonPath("$.cvAnalysisStatus").value("FAILED"));
    }

    @Test
    void blankNameOrEmailMeansTheProfileIsIncomplete() throws Exception {
        readiness(saveProfile(" ", "Martin", "alice@example.com", null, null, null))
                .andExpect(jsonPath("$.profileComplete").value(false));
        readiness(saveProfile("Alice", "", "alice@example.com", null, null, null))
                .andExpect(jsonPath("$.profileComplete").value(false));
        readiness(saveProfile("Alice", "Martin", " ", null, null, null))
                .andExpect(jsonPath("$.profileComplete").value(false));
    }

    @Test
    void unknownCandidateIs404() throws Exception {
        readiness(UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void isReservedToBackendServices() throws Exception {
        UUID id = saveProfile("Alice", "Martin", "alice@example.com", null, null, null);

        for (String role : new String[]{"CANDIDATE", "RECRUITER", "ADMIN"}) {
            mockMvc.perform(get("/internal/v1/candidates/" + id + "/readiness").with(as(verifier, id, role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/internal/v1/candidates/" + id + "/readiness")).andExpect(status().isUnauthorized());
    }
}
