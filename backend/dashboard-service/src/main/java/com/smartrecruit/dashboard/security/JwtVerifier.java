package com.smartrecruit.dashboard.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Verifies access tokens issued by auth-service (same HMAC secret, {@code JWT_SECRET}).
 */
@Component
public class JwtVerifier {

    private static final Set<String> ROLES = Set.of("CANDIDATE", "RECRUITER", "ADMIN");

    private final SecretKey key;

    public JwtVerifier(@Value("${auth.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** @return the authenticated user, or empty if the token is malformed, badly signed or expired */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String role = claims.get("role", String.class);
            if (claims.getSubject() == null || !ROLES.contains(role)) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(UUID.fromString(claims.getSubject()), role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
