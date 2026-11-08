package com.smartrecruit.application.dto;

import com.smartrecruit.application.model.InterviewMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Interview to plan (EXT-APP-02). {@code scheduledAt} must be in the future (checked against the service
 * clock), {@code durationMinutes} defaults to 60.
 */
public record InterviewRequest(
        @NotNull(message = "La date de l'entretien est obligatoire") Instant scheduledAt,
        @Min(value = 15, message = "Un entretien dure au moins 15 minutes")
        @Max(value = 480, message = "Un entretien dure au plus 480 minutes") Integer durationMinutes,
        @NotNull(message = "Le mode de l'entretien est obligatoire") InterviewMode mode,
        @Size(max = 500, message = "Le lieu ou le lien ne doit pas dépasser 500 caractères") String location) {
}
