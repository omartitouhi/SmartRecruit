package com.smartrecruit.application.dto;

import com.smartrecruit.application.model.ApplicationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/v1/applications/{id}/status} (BE-APP-04). {@code message} explains the decision to
 * the candidate (EXT-APP-01); {@code interview} is only accepted when moving to INTERVIEW (EXT-APP-02).
 */
public record StatusChangeRequest(
        @NotNull(message = "status est obligatoire") ApplicationStatus status,
        @Size(max = 2000, message = "Le message ne doit pas dépasser 2000 caractères") String message,
        @Valid InterviewRequest interview) {
}
