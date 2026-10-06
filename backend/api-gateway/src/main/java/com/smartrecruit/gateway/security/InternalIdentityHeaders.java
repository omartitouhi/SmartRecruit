package com.smartrecruit.gateway.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;

/**
 * Identity headers forwarded by the gateway once the JWT is validated (BE-SEC-04).
 * The signature is {@code base64url(HMAC-SHA256(secret, userId|role|timestamp))}; microservices
 * recompute it and reject the request if it differs or is too old.
 */
public final class InternalIdentityHeaders {

    public static final String USER_ID = "X-Auth-User-Id";
    public static final String USER_ROLE = "X-Auth-User-Role";
    public static final String TIMESTAMP = "X-Auth-Timestamp";
    public static final String SIGNATURE = "X-Auth-Signature";

    public static final List<String> ALL = List.of(USER_ID, USER_ROLE, TIMESTAMP, SIGNATURE);

    private InternalIdentityHeaders() {
    }

    public static String sign(String secret, String userId, String role, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((userId + "|" + role + "|" + timestamp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponible", e);
        }
    }
}
