package com.smartrecruit.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/applications/{id}/notes} (EXT-APP-03). */
public record NoteRequest(
        @NotBlank(message = "Le contenu de la note est obligatoire")
        @Size(max = 2000, message = "Une note ne doit pas dépasser 2000 caractères") String content,
        @Min(value = 1, message = "L'évaluation va de 1 à 5")
        @Max(value = 5, message = "L'évaluation va de 1 à 5") Integer rating) {
}
