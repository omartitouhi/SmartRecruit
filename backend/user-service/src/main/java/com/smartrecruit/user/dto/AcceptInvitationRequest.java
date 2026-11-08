package com.smartrecruit.user.dto;

import com.smartrecruit.user.security.RawText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/users/companies/invitations/accept} (BE-COMP-03).
 */
public record AcceptInvitationRequest(
        @NotBlank(message = "Le jeton d'invitation est obligatoire")
        @Size(max = 200, message = "Jeton d'invitation invalide")
        @RawText
        String token
) {
}
