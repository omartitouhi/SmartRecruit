package com.smartrecruit.dashboard.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-AUTH-04 acceptance criteria, exercised on test-only endpoints protected like real ones.
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

    @Value("${auth.jwt.secret}")
    private String secret;

    private String token(UUID userId, String role, Instant expiresAt, String signingSecret) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String token(String role) {
        return token(UUID.randomUUID(), role, Instant.now().plusSeconds(3600), secret);
    }

    private ResultActions call(String path, String jwt) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt));
    }

    @Test
    void candidateCallingRecruiterEndpointGets403() throws Exception {
        call("/api/v1/rbac-test/recruiter", token("CANDIDATE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/v1/rbac-test/recruiter"));
    }

    @Test
    void candidateCallingAdminEndpointGets403() throws Exception {
        call("/api/v1/rbac-test/admin", token("CANDIDATE")).andExpect(status().isForbidden());
    }

    @Test
    void recruiterCallingCandidateEndpointGets403() throws Exception {
        call("/api/v1/rbac-test/candidate", token("RECRUITER")).andExpect(status().isForbidden());
    }

    @Test
    void adminCallingAdminEndpointIsAllowed() throws Exception {
        call("/api/v1/rbac-test/admin", token("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void matchingRoleIsAllowedAndPrincipalIsExposed() throws Exception {
        UUID userId = UUID.randomUUID();
        call("/api/v1/rbac-test/candidate", token(userId, "CANDIDATE", Instant.now().plusSeconds(60), secret))
                .andExpect(status().isOk())
                .andExpect(content().string(userId.toString()));
        call("/api/v1/rbac-test/recruiter", token("RECRUITER")).andExpect(status().isOk());
    }

    @Test
    void missingTokenGets401() throws Exception {
        mockMvc.perform(get("/api/v1/rbac-test/any"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void malformedTokenGets401() throws Exception {
        call("/api/v1/rbac-test/any", "not-a-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenGets401() throws Exception {
        call("/api/v1/rbac-test/any", token(UUID.randomUUID(), "ADMIN", Instant.now().minusSeconds(60), secret))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherSecretGets401() throws Exception {
        String forged = token(UUID.randomUUID(), "ADMIN", Instant.now().plusSeconds(60),
                "another-secret-key-that-is-long-enough-for-hs256");
        call("/api/v1/rbac-test/admin", forged).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRoleGets401() throws Exception {
        call("/api/v1/rbac-test/any", token("SUPERUSER")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        mockMvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
