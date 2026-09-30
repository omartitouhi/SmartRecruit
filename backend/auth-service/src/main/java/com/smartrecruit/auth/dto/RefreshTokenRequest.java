package com.smartrecruit.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/v1/auth/refresh} (BE-AUTH-03) and {@code POST /api/v1/auth/logout} (BE-AUTH-05).
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Le refresh token est obligatoire")
        String refreshToken
) {
}
