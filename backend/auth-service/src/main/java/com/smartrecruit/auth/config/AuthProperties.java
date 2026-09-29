package com.smartrecruit.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tunables of the authentication module, bound from the {@code auth.*} keys of application.yml.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Jwt jwt, Lockout lockout) {

    /**
     * @param secret              HMAC key used to sign access tokens (>= 32 bytes)
     * @param accessTokenTtl      lifetime of the access JWT
     * @param refreshTokenTtl     lifetime of the opaque refresh token
     */
    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    /**
     * Brute-force protection on a single account (BE-AUTH-02).
     *
     * @param maxAttempts failed logins allowed inside {@code window} before locking
     * @param window      sliding window in which failures are counted
     * @param duration    how long the account stays locked
     */
    public record Lockout(int maxAttempts, Duration window, Duration duration) {
    }
}
