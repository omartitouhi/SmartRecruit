package com.smartrecruit.user.controller;

import com.smartrecruit.user.model.Company;
import com.smartrecruit.user.repository.CompanyRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /internal/v1/companies?ids=... — company names for job-service (BE-REC-01).
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalCompanyIntegrationTest {

    private static final UUID SERVICE_ID = new UUID(0, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CompanyRepository companyRepository;

    private final UUID acme = UUID.randomUUID();
    private final UUID globex = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        companyRepository.deleteAll();
        companyRepository.save(Company.builder().id(acme).name("Acme").sector("IT").logoUrl("/logos/acme.png").build());
        companyRepository.save(Company.builder().id(globex).name("Globex").sector("Finance").build());
    }

    @Test
    void returnsTheRequestedCompaniesAndSkipsUnknownIds() throws Exception {
        mockMvc.perform(get("/internal/v1/companies")
                        .param("ids", acme.toString(), UUID.randomUUID().toString())
                        .with(as(verifier, SERVICE_ID, "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(acme.toString()))
                .andExpect(jsonPath("$[0].name").value("Acme"))
                .andExpect(jsonPath("$[0].logoUrl").value("/logos/acme.png"))
                .andExpect(jsonPath("$[0].members").doesNotExist());
    }

    @Test
    void acceptsCommaSeparatedIds() throws Exception {
        mockMvc.perform(get("/internal/v1/companies").param("ids", acme + "," + globex)
                        .with(as(verifier, SERVICE_ID, "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void isReservedToBackendServices() throws Exception {
        mockMvc.perform(get("/internal/v1/companies").param("ids", acme.toString())
                        .with(as(verifier, UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/v1/companies").param("ids", acme.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findsTheCompanyOfAMember() throws Exception {
        UUID recruiter = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        companyRepository.save(Company.builder().id(UUID.randomUUID()).name("Initech").sector("IT")
                .logoUrl("https://initech.test/logo.png").members(new ArrayList<>(List.of(recruiter, teammate))).build());
        UUID initech = companyRepository.findByMember(teammate).orElseThrow().getId();

        mockMvc.perform(get("/internal/v1/companies/by-member/" + teammate).with(as(verifier, SERVICE_ID, "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(initech.toString()))
                .andExpect(jsonPath("$.name").value("Initech"))
                .andExpect(jsonPath("$.logoUrl").value("https://initech.test/logo.png"))
                .andExpect(jsonPath("$.members").doesNotExist());
    }

    @Test
    void byMemberIs404ForAnAccountWithoutCompany() throws Exception {
        mockMvc.perform(get("/internal/v1/companies/by-member/" + UUID.randomUUID())
                        .with(as(verifier, SERVICE_ID, "SERVICE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void byMemberIsReservedToBackendServices() throws Exception {
        mockMvc.perform(get("/internal/v1/companies/by-member/" + UUID.randomUUID())
                        .with(as(verifier, UUID.randomUUID(), "RECRUITER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/v1/companies/by-member/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
