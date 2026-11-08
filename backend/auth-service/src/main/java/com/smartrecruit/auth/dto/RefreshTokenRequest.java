package com.smartrecruit.auth.dto;

import com.smartrecruit.auth.security.RawText;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/v1/auth/refresh} (BE-AUTH-03) and {@code POST /api/v1/auth/logout} (BE-AUTH-05).
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Le refresh token est obligatoire")
        @RawText
        String refreshToken
) {
}
