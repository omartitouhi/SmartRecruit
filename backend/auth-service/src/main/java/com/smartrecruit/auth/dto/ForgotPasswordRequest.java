package com.smartrecruit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/v1/auth/forgot-password} (BE-AUTH-06).
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email n'est pas valide")
        String email
) {
}
