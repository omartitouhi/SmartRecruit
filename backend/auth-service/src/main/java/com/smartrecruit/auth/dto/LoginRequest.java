package com.smartrecruit.auth.dto;

import com.smartrecruit.auth.security.RawText;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/v1/auth/login} (BE-AUTH-02).
 */
public record LoginRequest(
        @NotBlank(message = "L'email est obligatoire")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @RawText
        String password
) {
}
