package com.smartrecruit.auth.dto;

/**
 * Returned by login and refresh. {@code expiresIn} is the access token lifetime in seconds.
 * Shape documented in {@code DATA_MODEL.md} §4.5.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {

    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
