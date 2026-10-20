package com.smartrecruit.dashboard.support;

import com.smartrecruit.dashboard.security.InternalIdentityVerifier;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

/** Adds the signed identity headers the api-gateway (or a calling service) would send. */
public final class SignedRequests {

    public static final UUID SERVICE_ID = new UUID(0, 0);

    private SignedRequests() {
    }

    public static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,
                                                   InternalIdentityVerifier verifier, UUID userId, String role) {
        long now = Instant.now().getEpochSecond();
        return request
                .header(InternalIdentityVerifier.USER_ID, userId.toString())
                .header(InternalIdentityVerifier.USER_ROLE, role)
                .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(now))
                .header(InternalIdentityVerifier.SIGNATURE, verifier.sign(userId.toString(), role, now));
    }

    public static MockHttpServletRequestBuilder asService(MockHttpServletRequestBuilder request,
                                                          InternalIdentityVerifier verifier) {
        return as(request, verifier, SERVICE_ID, "SERVICE");
    }
}
