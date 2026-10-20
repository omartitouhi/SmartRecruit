package com.smartrecruit.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /api/v1/dashboard/admin/jobs/{id}/moderation}. */
public record ModerationRequest(
        @NotBlank(message = "La raison de la modération est obligatoire")
        @Size(max = 500, message = "La raison ne peut pas dépasser 500 caractères")
        String reason
) {
}
