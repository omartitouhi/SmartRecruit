package com.smartrecruit.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of {@code POST /internal/v1/audit-events}; the timestamp is set by the server. */
public record AuditEventRequest(
        @NotNull(message = "L'auteur de l'action est obligatoire")
        UUID actorId,

        @NotBlank(message = "L'action est obligatoire")
        @Size(max = 64, message = "L'action ne peut pas dépasser 64 caractères")
        @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "L'action doit être un code en majuscules (ex. CV_VIEWED)")
        String action,

        @NotBlank(message = "Le type de ressource est obligatoire")
        @Size(max = 64, message = "Le type de ressource ne peut pas dépasser 64 caractères")
        String resourceType,

        @NotNull(message = "La ressource concernée est obligatoire")
        UUID resourceId
) {
}
