package com.smartrecruit.dashboard.support;

import com.smartrecruit.dashboard.security.InternalIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

/** Base of the tests that exercise calls to the other services, all served by {@link FakeServices}. */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractUpstreamIntegrationTest {

    protected static final FakeServices FAKE = FakeServices.INSTANCE;

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        registry.add("services.auth-service-uri", FAKE::uri);
        registry.add("services.user-service-uri", FAKE::uri);
        registry.add("services.job-service-uri", FAKE::uri);
        registry.add("services.application-service-uri", FAKE::uri);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected InternalIdentityVerifier verifier;

    @BeforeEach
    void resetFakeServices() {
        FAKE.reset();
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, UUID userId, String role) throws Exception {
        return mockMvc.perform(SignedRequests.as(request, verifier, userId, role));
    }

    protected ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return perform(request, UUID.randomUUID(), "ADMIN");
    }
}
