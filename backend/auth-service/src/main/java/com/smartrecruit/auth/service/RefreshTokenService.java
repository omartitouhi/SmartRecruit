package com.smartrecruit.auth.service;

import com.smartrecruit.auth.config.AuthProperties;
import com.smartrecruit.auth.model.RefreshToken;
import com.smartrecruit.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates opaque refresh tokens. The raw value is returned to the client once; only its
 * SHA-256 hash is persisted.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, AuthProperties properties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = properties.jwt().refreshTokenTtl();
        this.clock = clock;
    }

    /** @return the raw token to hand to the client */
    public String issue(UUID accountId) {
        String rawToken = SecureTokens.generate();
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(SecureTokens.hash(rawToken))
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
        return refreshTokenRepository.findByTokenHash(SecureTokens.hash(rawToken))
                .filter(token -> token.isActive(now))
                .map(token -> {
                    token.setRevokedAt(now);
                    return token.getAccountId();
                });
    }

    /** Revokes the token if it is still active; unknown tokens are ignored. */
    public void revoke(String rawToken) {
        Instant now = clock.instant();
        refreshTokenRepository.findByTokenHash(SecureTokens.hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(now));
    }

    /** Ends every session of the account (password change, account deletion...). */
    public void revokeAll(UUID accountId) {
        refreshTokenRepository.revokeAllActive(accountId, clock.instant());
    }
}
