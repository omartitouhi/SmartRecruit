package com.smartrecruit.application.security;

import com.smartrecruit.application.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A signed SERVICE identity is authenticated but holds no user role on public endpoints. */
class ServiceIdentityTest extends ApiTestSupport {

    @Test
    void serviceIdentityIsAcceptedButCannotUseCandidateEndpoints() throws Exception {
        mockMvc.perform(as(get("/api/v1/applications/me"), new UUID(0, 0), "SERVICE")).andExpect(status().isForbidden());
    }
}
