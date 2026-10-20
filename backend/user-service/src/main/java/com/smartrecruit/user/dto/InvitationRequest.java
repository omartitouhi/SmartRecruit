package com.smartrecruit.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/users/companies/{id}/invitations} (BE-COMP-03).
 */
public record InvitationRequest(
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email n'est pas valide")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères")
        String email
) {
}
