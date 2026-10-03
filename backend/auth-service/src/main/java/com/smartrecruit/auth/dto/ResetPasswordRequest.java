package com.smartrecruit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/reset-password} (BE-AUTH-06).
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Le token de réinitialisation est obligatoire")
        String token,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères")
        String newPassword
) {
}
