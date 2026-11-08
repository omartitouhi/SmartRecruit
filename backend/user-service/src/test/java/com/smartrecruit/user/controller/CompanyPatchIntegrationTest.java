package com.smartrecruit.user.controller;

import com.smartrecruit.user.model.Company;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-COMP-02 — PATCH /api/v1/users/companies/{id}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyPatchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CompanyRepository companyRepository;

    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private UUID companyId;

    @BeforeEach
    void setUp() {
        companyRepository.deleteAll();
        companyId = companyRepository.save(Company.builder().id(UUID.randomUUID()).name("Acme").sector("IT")
                .description("Old").logoUrl("https://acme.test/logo.png")
                .members(new ArrayList<>(List.of(member))).build()).getId();
    }

    private ResultActions patchAs(UUID account, String role, UUID id, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/companies/" + id).with(as(verifier, account, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void memberUpdatesOnlyTheSentFieldsAndTheyArePersisted() throws Exception {
        patchAs(member, "RECRUITER", companyId, "{\"name\": \" Acme Corp \", \"description\": \"New text\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.description").value("New text"))
                .andExpect(jsonPath("$.sector").value("IT"))
                .andExpect(jsonPath("$.logoUrl").value("https://acme.test/logo.png"))
                .andExpect(jsonPath("$.members[0]").value(member.toString()));

        Company stored = companyRepository.findById(companyId).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Acme Corp");
        assertThat(stored.getDescription()).isEqualTo("New text");
        assertThat(stored.getSector()).isEqualTo("IT");
    }

    @Test
    void nullClearsOptionalFieldsButNotMandatoryOnes() throws Exception {
        patchAs(member, "RECRUITER", companyId, "{\"description\": null, \"logoUrl\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.logoUrl").doesNotExist());

        patchAs(member, "RECRUITER", companyId, "{\"name\": null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
        assertThat(companyRepository.findById(companyId).orElseThrow().getName()).isEqualTo("Acme");
    }

    @Test
    void invalidValuesAreReportedWithFieldErrors() throws Exception {
        patchAs(member, "RECRUITER", companyId,
                "{\"name\": \"\", \"sector\": \"" + "s".repeat(101) + "\", \"logoUrl\": \"javascript:alert(1)\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.sector").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.logoUrl").isNotEmpty());
        patchAs(member, "RECRUITER", companyId, "[]").andExpect(status().isBadRequest());
    }

    @Test
    void aNonMemberRecruiterGets403AndNothingChanges() throws Exception {
        patchAs(outsider, "RECRUITER", companyId, "{\"name\": \"Hacked\"}").andExpect(status().isForbidden());

        assertThat(companyRepository.findById(companyId).orElseThrow().getName()).isEqualTo("Acme");
    }

    @Test
    void otherRolesAreForbidden() throws Exception {
        patchAs(member, "CANDIDATE", companyId, "{\"name\": \"X\"}").andExpect(status().isForbidden());
        patchAs(member, "ADMIN", companyId, "{\"name\": \"X\"}").andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/companies/" + companyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownCompanyIs404() throws Exception {
        patchAs(member, "RECRUITER", UUID.randomUUID(), "{\"name\": \"X\"}").andExpect(status().isNotFound());
    }
}
