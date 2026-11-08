package com.smartrecruit.user.dto;

import com.smartrecruit.user.model.Experience;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;

/**
 * One {@code Experience} item of a candidate profile ({@code DATA_MODEL.md} §3.2).
 * Dates are {@code "YYYY-MM"}; {@code endDate} is null for the current position.
 */
public record ExperienceRequest(
        @NotBlank(message = "L'intitulé du poste est obligatoire")
        @Size(max = 150, message = "L'intitulé ne doit pas dépasser 150 caractères")
        String title,

        @NotBlank(message = "L'entreprise est obligatoire")
        @Size(max = 150, message = "L'entreprise ne doit pas dépasser 150 caractères")
        String company,

        @NotNull(message = "La date de début est obligatoire (format YYYY-MM)")
        YearMonth startDate,

        YearMonth endDate,

        @Size(max = 2000, message = "La description ne doit pas dépasser 2000 caractères")
        String description
) {

    @AssertTrue(message = "La date de fin doit être postérieure ou égale à la date de début")
    public boolean isEndDateAfterStartDate() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    public Experience toEntity() {
        return Experience.builder()
                .title(title.trim())
                .company(company.trim())
                .startDate(startDate)
                .endDate(endDate)
                .description(description)
                .build();
    }
}
