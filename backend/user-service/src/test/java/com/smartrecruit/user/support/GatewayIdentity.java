package com.smartrecruit.user.support;

import com.smartrecruit.user.security.InternalIdentityVerifier;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

/**
 * Adds the identity headers the api-gateway forwards after validating the JWT (BE-SEC-04).
 */
public final class GatewayIdentity {

    private GatewayIdentity() {
    }

    public static RequestPostProcessor as(InternalIdentityVerifier verifier, UUID userId, String role) {
        return request -> {
            long now = Instant.now().getEpochSecond();
            request.addHeader(InternalIdentityVerifier.USER_ID, userId.toString());
            request.addHeader(InternalIdentityVerifier.USER_ROLE, role);
            request.addHeader(InternalIdentityVerifier.TIMESTAMP, Long.toString(now));
            request.addHeader(InternalIdentityVerifier.SIGNATURE, verifier.sign(userId.toString(), role, now));
            return request;
        };
    }
}
