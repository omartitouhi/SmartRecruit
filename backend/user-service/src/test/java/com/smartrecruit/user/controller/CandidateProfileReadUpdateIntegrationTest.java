package com.smartrecruit.user.controller;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.user.repository.CandidateProfileRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static com.smartrecruit.user.controller.CandidateProfileCreateIntegrationTest.FULL_PROFILE;
import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-USER-03 — GET and PATCH /api/v1/users/candidates/me.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CandidateProfileReadUpdateIntegrationTest {

    private static final String ME = "/api/v1/users/candidates/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CandidateProfileRepository repository;

    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        mockMvc.perform(put(ME).with(as(verifier, candidateId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(FULL_PROFILE))
                .andExpect(status().isCreated());
    }

    private ResultActions patchProfile(String body) throws Exception {
        return mockMvc.perform(patch(ME).with(as(verifier, candidateId, "CANDIDATE"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions getProfile() throws Exception {
        return mockMvc.perform(get(ME).with(as(verifier, candidateId, "CANDIDATE")));
    }

    private static String field(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    @Test
    void getReturnsTheCurrentProfile() throws Exception {
        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(candidateId.toString()))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice.martin@example.com"))
                .andExpect(jsonPath("$.skills[1]").value("Spring Boot"))
                .andExpect(jsonPath("$.experiences[0].company").value("Acme"));
    }

    @Test
    void getWithoutProfileReturns404() throws Exception {
        mockMvc.perform(get(ME).with(as(verifier, UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void patchOnlyChangesTheFieldsSentAndRefreshesUpdatedAt() throws Exception {
        String createdAt = field(getProfile(), "$.createdAt");
        Instant updatedBefore = Instant.parse(field(getProfile(), "$.updatedAt"));
        Thread.sleep(20);

        patchProfile("""
                {"location": "Sousse", "skills": ["Kotlin", "Docker"]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Sousse"))
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.skills[0]").value("Kotlin"))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.phone").value("+216 20 123 456"))
                .andExpect(jsonPath("$.experiences.length()").value(1))
                .andExpect(jsonPath("$.languages.length()").value(2));

        ResultActions after = getProfile()
                .andExpect(jsonPath("$.location").value("Sousse"))
                .andExpect(jsonPath("$.createdAt").value(createdAt));
        assertThat(Instant.parse(field(after, "$.updatedAt"))).isAfter(updatedBefore);
    }

    @Test
    void explicitNullClearsOptionalFields() throws Exception {
        patchProfile("""
                {"phone": null, "location": null, "experiences": null, "languages": []}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.experiences.length()").value(0))
                .andExpect(jsonPath("$.languages.length()").value(0))
                .andExpect(jsonPath("$.skills.length()").value(2));
    }

    @Test
    void mandatoryFieldsCannotBeClearedOrInvalid() throws Exception {
        patchProfile("""
                {"lastName": null, "email": "not-an-email", "firstName": "  "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists());

        getProfile().andExpect(jsonPath("$.lastName").value("Martin"));
    }

    @Test
    void invalidNestedItemIsRejected() throws Exception {
        patchProfile("""
                {"experiences": [{"title": "", "company": "Acme", "startDate": "2020-01"}]}
                """)
                .andExpect(status().isBadRequest());

        getProfile().andExpect(jsonPath("$.experiences[0].title").value("Backend Developer"));
    }

    @Test
    void emptyPatchKeepsEverything() throws Exception {
        patchProfile("{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Martin"))
                .andExpect(jsonPath("$.skills.length()").value(2));
    }

    @Test
    void patchWithoutProfileReturns404() throws Exception {
        mockMvc.perform(patch(ME).with(as(verifier, UUID.randomUUID(), "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"location\": \"Sfax\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyCandidatesCanReadOrPatchTheirProfile() throws Exception {
        mockMvc.perform(get(ME).with(as(verifier, candidateId, "RECRUITER"))).andExpect(status().isForbidden());
        mockMvc.perform(patch(ME).with(as(verifier, candidateId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
    }
}
