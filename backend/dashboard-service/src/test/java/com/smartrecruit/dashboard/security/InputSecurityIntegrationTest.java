package com.smartrecruit.dashboard.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-SEC-03 acceptance criteria on a test-only endpoint echoing what a controller receives.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InputSecurityIntegrationTest.EchoEndpoint.class)
class InputSecurityIntegrationTest {

    record EchoRequest(String description, List<String> tags, @RawText String password) {
    }

    @TestConfiguration
    @RestController
    static class EchoEndpoint {

        @PostMapping("/api/v1/input-security-test/echo")
        EchoRequest echo(@RequestBody EchoRequest request) {
            return request;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        return request
                .header(InternalIdentityVerifier.USER_ID, userId.toString())
                .header(InternalIdentityVerifier.USER_ROLE, "RECRUITER")
                .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(now))
                .header(InternalIdentityVerifier.SIGNATURE, verifier.sign(userId.toString(), "RECRUITER", now));
    }

    @Test
    void htmlAndScriptsAreRemovedFromFreeTextBeforeReachingTheController() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/input-security-test/echo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Poste <b>Java</b><script>alert('xss')</script><img src=x onerror=alert(1)>\\nTeam R&D",
                                 "tags": ["<a href=\\"javascript:alert(1)\\">Spring</a>", "&lt;script&gt;alert(1)&lt;/script&gt;Kotlin"],
                                 "password": "<Secret>&123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Poste Java\nTeam R&D"))
                .andExpect(jsonPath("$.tags[0]").value("Spring"))
                .andExpect(jsonPath("$.tags[1]").value("Kotlin"))
                .andExpect(jsonPath("$.password").value("<Secret>&123"));
    }

    @Test
    void plainTextIsKeptAsIs() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/input-security-test/echo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "5 < 10 && 3 > 2 — C'est \\"OK\\"\\n\\tligne 2", "tags": ["C#", "Node.js"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("5 < 10 && 3 > 2 — C'est \"OK\"\n\tligne 2"))
                .andExpect(jsonPath("$.tags[0]").value("C#"));
    }

    @Test
    void responsesCarrySecurityHeadersAndNoSessionCookie() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void stateChangingRequestWithoutAuthorizationIsRejected() throws Exception {
        // CSRF: credentials travel in a header, never in a cookie, so a forged cross-site request has none.
        mockMvc.perform(post("/api/v1/input-security-test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
