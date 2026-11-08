package com.smartrecruit.user.dto;

import com.smartrecruit.user.model.Education;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One {@code Education} item of a candidate profile ({@code DATA_MODEL.md} §3.2).
 */
public record EducationRequest(
        @NotBlank(message = "Le diplôme est obligatoire")
        @Size(max = 150, message = "Le diplôme ne doit pas dépasser 150 caractères")
        String degree,

        @NotBlank(message = "L'établissement est obligatoire")
        @Size(max = 150, message = "L'établissement ne doit pas dépasser 150 caractères")
        String institution,

        @Min(value = 1950, message = "L'année doit être comprise entre 1950 et 2100")
        @Max(value = 2100, message = "L'année doit être comprise entre 1950 et 2100")
        Integer year
) {

    public Education toEntity() {
        return Education.builder()
                .degree(degree.trim())
                .institution(institution.trim())
                .year(year)
                .build();
    }
}
