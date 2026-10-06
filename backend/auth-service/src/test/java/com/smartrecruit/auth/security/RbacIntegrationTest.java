package com.smartrecruit.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-AUTH-04 / BE-SEC-04 acceptance criteria, exercised on test-only endpoints protected like real
 * ones. Requests carry the signed identity headers the api-gateway adds after validating the JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RbacIntegrationTest.RbacTestEndpoints.class)
class RbacIntegrationTest {

    @TestConfiguration
    @RestController
    static class RbacTestEndpoints {

        @GetMapping("/api/v1/rbac-test/candidate")
        @PreAuthorize("hasRole('CANDIDATE')")
        String candidateOnly(@AuthenticationPrincipal AuthenticatedUser user) {
            return user.id().toString();
        }

        @GetMapping("/api/v1/rbac-test/recruiter")
        @PreAuthorize("hasRole('RECRUITER')")
        String recruiterOnly() {
            return "recruiter";
        }

        @GetMapping("/api/v1/rbac-test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "admin";
        }

        @GetMapping("/api/v1/rbac-test/any")
        String anyAuthenticated() {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID userId, String role,
                                             long timestamp, String signature) {
        return request
                .header(InternalIdentityVerifier.USER_ID, userId.toString())
                .header(InternalIdentityVerifier.USER_ROLE, role)
                .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(timestamp))
                .header(InternalIdentityVerifier.SIGNATURE, signature);
    }

    private ResultActions call(String path, UUID userId, String role) throws Exception {
        long now = Instant.now().getEpochSecond();
        return mockMvc.perform(as(get(path), userId, role, now, verifier.sign(userId.toString(), role, now)));
    }

    private ResultActions call(String path, String role) throws Exception {
        return call(path, UUID.randomUUID(), role);
    }

    @Test
    void candidateCallingRecruiterEndpointGets403() throws Exception {
        call("/api/v1/rbac-test/recruiter", "CANDIDATE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/v1/rbac-test/recruiter"));
    }

    @Test
    void candidateCallingAdminEndpointGets403() throws Exception {
        call("/api/v1/rbac-test/admin", "CANDIDATE").andExpect(status().isForbidden());
    }

    @Test
    void recruiterCallingCandidateEndpointGets403() throws Exception {
        call("/api/v1/rbac-test/candidate", "RECRUITER").andExpect(status().isForbidden());
    }

    @Test
    void adminCallingAdminEndpointIsAllowed() throws Exception {
        call("/api/v1/rbac-test/admin", "ADMIN")
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void matchingRoleIsAllowedAndPrincipalIsExposed() throws Exception {
        UUID userId = UUID.randomUUID();
        call("/api/v1/rbac-test/candidate", userId, "CANDIDATE")
                .andExpect(status().isOk())
                .andExpect(content().string(userId.toString()));
        call("/api/v1/rbac-test/recruiter", "RECRUITER").andExpect(status().isOk());
    }

    @Test
    void missingIdentityGets401() throws Exception {
        mockMvc.perform(get("/api/v1/rbac-test/any"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rawJwtWithoutGatewayHeadersGets401() throws Exception {
        mockMvc.perform(get("/api/v1/rbac-test/any").header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.e30.x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedSignatureGets401() throws Exception {
        long now = Instant.now().getEpochSecond();
        mockMvc.perform(as(get("/api/v1/rbac-test/admin"), UUID.randomUUID(), "ADMIN", now, "forged-signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void escalatedRoleWithSignatureOfAnotherRoleGets401() throws Exception {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String candidateSignature = verifier.sign(userId.toString(), "CANDIDATE", now);
        mockMvc.perform(as(get("/api/v1/rbac-test/admin"), userId, "ADMIN", now, candidateSignature))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staleHeadersGet401() throws Exception {
        UUID userId = UUID.randomUUID();
        long old = Instant.now().minus(InternalIdentityVerifier.MAX_AGE).getEpochSecond() - 5;
        mockMvc.perform(as(get("/api/v1/rbac-test/any"), userId, "ADMIN", old, verifier.sign(userId.toString(), "ADMIN", old)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRoleGets401() throws Exception {
        call("/api/v1/rbac-test/any", "SUPERUSER").andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        mockMvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
