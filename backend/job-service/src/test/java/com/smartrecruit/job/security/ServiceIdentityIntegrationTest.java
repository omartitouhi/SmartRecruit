package com.smartrecruit.job.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The signed {@code SERVICE} identity used between backends is accepted, and only on endpoints
 * that ask for it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ServiceIdentityIntegrationTest.ServiceOnlyEndpoint.class)
class ServiceIdentityIntegrationTest {

    @TestConfiguration
    @RestController
    static class ServiceOnlyEndpoint {

        @GetMapping("/internal/v1/service-identity-test")
        @PreAuthorize("hasRole('SERVICE')")
        String serviceOnly() {
            return "service";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    private ResultActions call(String role) throws Exception {
        UUID id = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        return mockMvc.perform(get("/internal/v1/service-identity-test")
                .header(InternalIdentityVerifier.USER_ID, id.toString())
                .header(InternalIdentityVerifier.USER_ROLE, role)
                .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(now))
                .header(InternalIdentityVerifier.SIGNATURE, verifier.sign(id.toString(), role, now)));
    }

    @Test
    void signedServiceIdentityIsAccepted() throws Exception {
        call("SERVICE").andExpect(status().isOk());
    }

    @Test
    void userRolesCannotCallServiceEndpoints() throws Exception {
        call("ADMIN").andExpect(status().isForbidden());
        call("RECRUITER").andExpect(status().isForbidden());
    }

    @Test
    void unsignedServiceRoleIsRejected() throws Exception {
        mockMvc.perform(get("/internal/v1/service-identity-test")
                        .header(InternalIdentityVerifier.USER_ID, UUID.randomUUID().toString())
                        .header(InternalIdentityVerifier.USER_ROLE, "SERVICE")
                        .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(Instant.now().getEpochSecond()))
                        .header(InternalIdentityVerifier.SIGNATURE, "forged"))
                .andExpect(status().isUnauthorized());
    }
}
