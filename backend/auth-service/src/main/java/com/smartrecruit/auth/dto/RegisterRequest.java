package com.smartrecruit.auth.dto;

import com.smartrecruit.auth.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/register} (BE-AUTH-01).
 * Only {@code CANDIDATE} and {@code RECRUITER} can be self-assigned; {@code ADMIN} is rejected.
 */
public record RegisterRequest(
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email n'est pas valide")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères")
        String password,

        @NotNull(message = "Le rôle est obligatoire (CANDIDATE ou RECRUITER)")
        Role role
) {
}
