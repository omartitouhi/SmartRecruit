package com.smartrecruit.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of {@code POST /api/v1/applications}; {@code coverLetter} is optional (EXT-APP-01), blank means none. */
public record ApplyRequest(
        @NotNull(message = "jobId est obligatoire") UUID jobId,
        @Size(max = 5000, message = "La lettre de motivation ne doit pas dépasser 5000 caractères") String coverLetter) {
}
