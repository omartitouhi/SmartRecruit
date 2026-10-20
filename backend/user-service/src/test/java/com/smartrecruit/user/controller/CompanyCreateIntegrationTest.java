package com.smartrecruit.user.controller;

import com.smartrecruit.user.repository.CompanyRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-COMP-01 — POST /api/v1/users/companies, GET /companies/me and GET /companies/{id}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyCreateIntegrationTest {

    static final String ACME = """
            {"name": " Acme ", "sector": "IT", "description": "Software house", "logoUrl": "https://acme.test/logo.png"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CompanyRepository companyRepository;

    private final UUID recruiter = UUID.randomUUID();

    @BeforeEach
    void clean() {
        companyRepository.deleteAll();
    }

    private ResultActions create(UUID account, String role, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/users/companies").with(as(verifier, account, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void recruiterCreatesACompanyAndBecomesItsFirstMember() throws Exception {
        create(recruiter, "RECRUITER", ACME)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.sector").value("IT"))
                .andExpect(jsonPath("$.description").value("Software house"))
                .andExpect(jsonPath("$.logoUrl").value("https://acme.test/logo.png"))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0]").value(recruiter.toString()));
        assertThat(companyRepository.findByMember(recruiter)).isPresent();
    }

    @Test
    void descriptionAndLogoAreOptional() throws Exception {
        create(recruiter, "RECRUITER", "{\"name\": \"Acme\", \"sector\": \"IT\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.logoUrl").doesNotExist());
    }

    @Test
    void aSecondCompanyForTheSameRecruiterIsRejected() throws Exception {
        create(recruiter, "RECRUITER", ACME).andExpect(status().isCreated());

        create(recruiter, "RECRUITER", "{\"name\": \"Globex\", \"sector\": \"Finance\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(companyRepository.count()).isEqualTo(1);
    }

    @Test
    void candidatesAndAdminsCannotCreateCompanies() throws Exception {
        create(UUID.randomUUID(), "CANDIDATE", ACME).andExpect(status().isForbidden());
        create(UUID.randomUUID(), "ADMIN", ACME).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/companies").contentType(MediaType.APPLICATION_JSON).content(ACME))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidFieldsAreReportedWithFieldErrors() throws Exception {
        create(recruiter, "RECRUITER", """
                {"name": " ", "sector": "%s", "description": "%s", "logoUrl": "ftp://acme.test/logo.png"}
                """.formatted("s".repeat(101), "d".repeat(5001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.sector").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.description").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.logoUrl").isNotEmpty());
        create(recruiter, "RECRUITER", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.sector").isNotEmpty());
        assertThat(companyRepository.count()).isZero();
    }

    @Test
    void htmlInTextFieldsIsStrippedBeforeStorage() throws Exception {
        create(recruiter, "RECRUITER",
                "{\"name\": \"<b>Acme</b><script>alert(1)</script>\", \"sector\": \"IT\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    void meReturnsTheRecruitersCompanyWithItsMembers() throws Exception {
        create(recruiter, "RECRUITER", ACME).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/users/companies/me").with(as(verifier, recruiter, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.members[0]").value(recruiter.toString()));
    }

    @Test
    void meIs404WithoutACompanyAndForbiddenToOtherRoles() throws Exception {
        mockMvc.perform(get("/api/v1/users/companies/me").with(as(verifier, recruiter, "RECRUITER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/companies/me").with(as(verifier, recruiter, "CANDIDATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyConnectedUserSeesThePublicViewWithoutMembers() throws Exception {
        String body = create(recruiter, "RECRUITER", ACME).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        for (String role : new String[]{"CANDIDATE", "RECRUITER", "ADMIN"}) {
            mockMvc.perform(get("/api/v1/users/companies/" + id).with(as(verifier, UUID.randomUUID(), role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.name").value("Acme"))
                    .andExpect(jsonPath("$.sector").value("IT"))
                    .andExpect(jsonPath("$.description").value("Software house"))
                    .andExpect(jsonPath("$.logoUrl").value("https://acme.test/logo.png"))
                    .andExpect(jsonPath("$.members").doesNotExist());
        }
        mockMvc.perform(get("/api/v1/users/companies/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownCompanyIs404() throws Exception {
        mockMvc.perform(get("/api/v1/users/companies/" + UUID.randomUUID())
                        .with(as(verifier, UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isNotFound());
    }
}
