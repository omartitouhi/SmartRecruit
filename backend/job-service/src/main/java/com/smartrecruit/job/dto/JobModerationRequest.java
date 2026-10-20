package com.smartrecruit.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload of {@code PATCH /internal/v1/jobs/{id}/moderation} (BE-ADMIN-02). */
public record JobModerationRequest(
        @NotBlank(message = "La raison de la modération est obligatoire")
        @Size(max = 500, message = "La raison ne doit pas dépasser 500 caractères")
        String reason
) {
}
