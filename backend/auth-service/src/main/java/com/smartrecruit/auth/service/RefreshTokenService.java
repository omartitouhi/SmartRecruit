package com.smartrecruit.auth.service;

import com.smartrecruit.auth.config.AuthProperties;
import com.smartrecruit.auth.model.RefreshToken;
import com.smartrecruit.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates opaque refresh tokens. The raw value is returned to the client once; only its
 * SHA-256 hash is persisted.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenTtl;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, AuthProperties properties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = properties.jwt().refreshTokenTtl();
        this.clock = clock;
    }

    /** @return the raw token to hand to the client */
    public String issue(UUID accountId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .accountId(accountId)
                .expiresAt(clock.instant().plus(refreshTokenTtl))
                .build());
        return rawToken;
    }

    /**
     * Single-use exchange: the token is revoked as soon as it is consumed (rotation).
     *
     * @return the owning account id, or empty if the token is unknown, expired or revoked
     */
    public Optional<UUID> consume(String rawToken) {
        Instant now = clock.instant();
        return refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.isActive(now))
                .map(token -> {
                    token.setRevokedAt(now);
                    return token.getAccountId();
                });
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
