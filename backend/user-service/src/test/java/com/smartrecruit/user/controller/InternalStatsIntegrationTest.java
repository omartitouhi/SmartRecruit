package com.smartrecruit.user.controller;

import com.smartrecruit.user.model.CandidateProfile;
import com.smartrecruit.user.model.Company;
import com.smartrecruit.user.repository.CandidateProfileRepository;
import com.smartrecruit.user.repository.CompanyRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /internal/v1/stats — counters for dashboard-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalStatsIntegrationTest {

    private static final UUID SERVICE_ID = new UUID(0, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CandidateProfileRepository profileRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void countsCandidateProfilesAndCompanies() throws Exception {
        profileRepository.deleteAll();
        companyRepository.deleteAll();
        for (int i = 0; i < 3; i++) {
            profileRepository.save(CandidateProfile.builder().id(UUID.randomUUID()).firstName("A").lastName("B")
                    .email("a" + i + "@b.test").build());
        }
        companyRepository.save(Company.builder().id(UUID.randomUUID()).name("Acme").sector("IT").build());
        companyRepository.save(Company.builder().id(UUID.randomUUID()).name("Globex").sector("IT").build());

        mockMvc.perform(get("/internal/v1/stats").with(as(verifier, SERVICE_ID, "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateProfiles").value(3))
                .andExpect(jsonPath("$.companies").value(2));
    }

    @Test
    void returnsZerosWhenEmpty() throws Exception {
        profileRepository.deleteAll();
        companyRepository.deleteAll();

        mockMvc.perform(get("/internal/v1/stats").with(as(verifier, SERVICE_ID, "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateProfiles").value(0))
                .andExpect(jsonPath("$.companies").value(0));
    }

    @Test
    void isReservedToBackendServices() throws Exception {
        for (String role : new String[]{"CANDIDATE", "RECRUITER", "ADMIN"}) {
            mockMvc.perform(get("/internal/v1/stats").with(as(verifier, UUID.randomUUID(), role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/internal/v1/stats")).andExpect(status().isUnauthorized());
    }
}
