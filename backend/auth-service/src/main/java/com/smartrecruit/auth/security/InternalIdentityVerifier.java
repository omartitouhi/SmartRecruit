package com.smartrecruit.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the identity forwarded by the api-gateway once it has validated the JWT (BE-SEC-04).
 * The headers are trusted only if their HMAC-SHA256 signature matches {@code INTERNAL_AUTH_SECRET}
 * and they are recent, so a request that bypasses the gateway cannot forge an identity.
 */
@Component
public class InternalIdentityVerifier {

    public static final String USER_ID = "X-Auth-User-Id";
    public static final String USER_ROLE = "X-Auth-User-Role";
    public static final String TIMESTAMP = "X-Auth-Timestamp";
    public static final String SIGNATURE = "X-Auth-Signature";

    static final Duration MAX_AGE = Duration.ofMinutes(2);
    /**
     * {@code SERVICE} identifies backend-to-backend calls on {@code /internal/**} (BE-SEC-02). The gateway
     * never forwards it: it only accepts JWTs carrying CANDIDATE, RECRUITER or ADMIN.
     */
    private static final Set<String> ROLES = Set.of("CANDIDATE", "RECRUITER", "ADMIN", "SERVICE");

    private final byte[] secret;
    private final Clock clock;

    public InternalIdentityVerifier(@Value("${internal-auth.secret}") String secret, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /** @return the caller identity, or empty if the headers are missing, forged or stale */
    public Optional<AuthenticatedUser> verify(HttpServletRequest request) {
        String userId = request.getHeader(USER_ID);
        String role = request.getHeader(USER_ROLE);
        String timestamp = request.getHeader(TIMESTAMP);
        String signature = request.getHeader(SIGNATURE);
        if (userId == null || role == null || timestamp == null || signature == null || !ROLES.contains(role)) {
            return Optional.empty();
        }
        try {
            long issuedAt = Long.parseLong(timestamp);
            long age = clock.instant().getEpochSecond() - issuedAt;
            if (Math.abs(age) > MAX_AGE.toSeconds()) {
                return Optional.empty();
            }
            byte[] expected = sign(userId, role, issuedAt).getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(UUID.fromString(userId), role));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Same algorithm as {@code InternalIdentityHeaders.sign} in the api-gateway. */
    public String sign(String userId, String role, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((userId + "|" + role + "|" + timestamp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponible", e);
        }
    }
}
