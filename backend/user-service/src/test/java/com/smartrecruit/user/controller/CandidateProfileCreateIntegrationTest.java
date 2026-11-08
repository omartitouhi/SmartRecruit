package com.smartrecruit.user.controller;

import com.smartrecruit.user.model.CandidateProfile;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.YearMonth;
import java.util.UUID;

import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-USER-01 — PUT /api/v1/users/candidates/me.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CandidateProfileCreateIntegrationTest {

    static final String FULL_PROFILE = """
            {
              "firstName": " Alice ",
              "lastName": "Martin",
              "email": "Alice.Martin@Example.com",
              "phone": "+216 20 123 456",
              "location": "Tunis",
              "skills": ["Java", "Spring Boot", "Java"],
              "experiences": [
                {"title": "Backend Developer", "company": "Acme", "startDate": "2022-01", "endDate": null,
                 "description": "APIs REST"}
              ],
              "education": [
                {"degree": "Master Génie Logiciel", "institution": "ESPRIT", "year": 2025}
              ],
              "languages": ["Français", "Anglais"]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CandidateProfileRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private ResultActions putProfile(UUID userId, String role, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/users/candidates/me")
                .with(as(verifier, userId, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void firstPutCreatesTheProfileLinkedToTheAccount() throws Exception {
        putProfile(candidateId, "CANDIDATE", FULL_PROFILE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(candidateId.toString()))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice.martin@example.com"))
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.experiences[0].startDate").value("2022-01"))
                .andExpect(jsonPath("$.experiences[0].endDate").doesNotExist())
                .andExpect(jsonPath("$.education[0].year").value(2025))
                .andExpect(jsonPath("$.languages[1]").value("Anglais"))
                .andExpect(jsonPath("$.cvUrl").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        transactionTemplate.executeWithoutResult(status -> {
            CandidateProfile saved = repository.findById(candidateId).orElseThrow();
            assertThat(saved.getLastName()).isEqualTo("Martin");
            assertThat(saved.getLocation()).isEqualTo("Tunis");
            assertThat(saved.getSkills()).containsExactly("Java", "Spring Boot");
            assertThat(saved.getExperiences().getFirst().getStartDate()).isEqualTo(YearMonth.of(2022, 1));
        });
    }

    @Test
    void secondPutReplacesTheProfile() throws Exception {
        putProfile(candidateId, "CANDIDATE", FULL_PROFILE).andExpect(status().isCreated());

        putProfile(candidateId, "CANDIDATE", """
                {"firstName": "Alice", "lastName": "Durand", "email": "alice@example.com", "skills": ["Python"]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Durand"))
                .andExpect(jsonPath("$.skills[0]").value("Python"))
                .andExpect(jsonPath("$.experiences.length()").value(0))
                .andExpect(jsonPath("$.phone").doesNotExist());

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void missingNameAndEmailAreRejectedWith400() throws Exception {
        putProfile(candidateId, "CANDIDATE", """
                {"firstName": "Alice", "lastName": "  "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        assertThat(repository.count()).isZero();
    }

    @Test
    void invalidNestedValuesAreRejectedWith400() throws Exception {
        putProfile(candidateId, "CANDIDATE", """
                {"firstName": "Alice", "lastName": "Martin", "email": "not-an-email", "phone": "abc",
                 "skills": [""],
                 "experiences": [{"title": "Dev", "company": "Acme", "startDate": "2023-05", "endDate": "2022-01"}],
                 "education": [{"degree": "", "institution": "ESPRIT", "year": 1800}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors['skills[0]']").exists())
                .andExpect(jsonPath("$.fieldErrors['experiences[0].endDateAfterStartDate']").exists())
                .andExpect(jsonPath("$.fieldErrors['education[0].degree']").exists())
                .andExpect(jsonPath("$.fieldErrors['education[0].year']").exists());
    }

    @Test
    void malformedDateIsRejectedWith400() throws Exception {
        putProfile(candidateId, "CANDIDATE", """
                {"firstName": "Alice", "lastName": "Martin", "email": "a@b.com",
                 "experiences": [{"title": "Dev", "company": "Acme", "startDate": "janvier 2022"}]}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void eachCandidateOnlyWritesTheirOwnProfile() throws Exception {
        UUID otherCandidate = UUID.randomUUID();
        putProfile(candidateId, "CANDIDATE", FULL_PROFILE).andExpect(status().isCreated());
        putProfile(otherCandidate, "CANDIDATE", """
                {"firstName": "Bob", "lastName": "Leroy", "email": "bob@example.com"}
                """).andExpect(status().isCreated());

        assertThat(repository.findById(candidateId).orElseThrow().getFirstName()).isEqualTo("Alice");
        assertThat(repository.findById(otherCandidate).orElseThrow().getFirstName()).isEqualTo("Bob");
    }

    @Test
    void htmlInFreeTextIsSanitizedBeforeStorage() throws Exception {
        putProfile(candidateId, "CANDIDATE", """
                {"firstName": "<b>Alice</b>", "lastName": "Martin<script>alert(1)</script>", "email": "a@b.com",
                 "skills": ["<img src=x onerror=alert(1)>Java"],
                 "experiences": [{"title": "Dev", "company": "Acme", "startDate": "2022-01",
                                  "description": "APIs <a href=\\"javascript:alert(1)\\">REST</a>\\nR&D"}]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Martin"))
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.experiences[0].description").value("APIs REST\nR&D"));

        transactionTemplate.executeWithoutResult(status ->
                assertThat(repository.findById(candidateId).orElseThrow().getExperiences().getFirst().getDescription())
                        .isEqualTo("APIs REST\nR&D"));
    }

    @Test
    void sqlInjectionPayloadIsStoredAsPlainText() throws Exception {
        String payload = "Robert'); DROP TABLE candidate_profiles; --";

        putProfile(candidateId, "CANDIDATE", """
                {"firstName": "Bobby", "lastName": "%s", "email": "bobby@example.com"}
                """.formatted(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastName").value(payload));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(candidateId).orElseThrow().getLastName()).isEqualTo(payload);
    }

    @Test
    void recruiterCannotCreateACandidateProfile() throws Exception {
        putProfile(candidateId, "RECRUITER", FULL_PROFILE).andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallIsRejectedWith401() throws Exception {
        mockMvc.perform(put("/api/v1/users/candidates/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_PROFILE))
                .andExpect(status().isUnauthorized());
    }
}
