package com.smartrecruit.job.dto;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import com.smartrecruit.job.model.WorkMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Values of a {@code PATCH /api/v1/jobs/{id}} body (BE-JOB-02), JSON Merge Patch semantics: a
 * {@code null} here means "absent" or "sent as null", {@link #presentFields} tells them apart.
 * Absent fields keep their value; every field of an offer is mandatory, so sending one as
 * {@code null} is rejected; lists are replaced as a whole. Same limits as {@link JobCreateRequest}.
 * EXT-JOB-01: the salary bounds and the deadline are optional and may be removed with {@code null}
 * ({@link #NULLABLE_FIELDS}); {@code workMode} and {@code positions} may not.
 */
public record JobPatchRequest(
        @Pattern(regexp = ".*\\S.*", message = "Le titre est obligatoire")
        @Size(max = 255, message = "Le titre ne doit pas dépasser 255 caractères")
        String title,

        @Pattern(regexp = "(?s).*\\S.*", message = "La description est obligatoire")
        @Size(min = 20, max = 20000, message = "La description doit contenir entre 20 et 20000 caractères")
        String description,

        @Size(min = 1, max = 50, message = "Entre 1 et 50 compétences requises")
        List<@NotBlank(message = "Une compétence ne peut pas être vide")
        @Size(max = 100, message = "Une compétence ne doit pas dépasser 100 caractères") String> requiredSkills,

        ExperienceLevel experienceLevel,

        ContractType contractType,

        @Pattern(regexp = ".*\\S.*", message = "La localisation est obligatoire")
        @Size(max = 255, message = "La localisation ne doit pas dépasser 255 caractères")
        String location,

        WorkMode workMode,

        @Min(value = 0, message = "Le salaire minimum doit être positif")
        @Max(value = 1_000_000, message = "Le salaire minimum ne doit pas dépasser 1 000 000")
        Integer salaryMin,

        @Min(value = 0, message = "Le salaire maximum doit être positif")
        @Max(value = 1_000_000, message = "Le salaire maximum ne doit pas dépasser 1 000 000")
        Integer salaryMax,

        LocalDate applicationDeadline,

        @Min(value = 1, message = "Il faut au moins un poste")
        @Max(value = 100, message = "100 postes au plus")
        Integer positions,

        Set<String> presentFields
) {

    public static final Set<String> FIELDS = Set.of("title", "description", "requiredSkills", "experienceLevel",
            "contractType", "location", "workMode", "salaryMin", "salaryMax", "applicationDeadline", "positions");

    /** Optional fields of an offer: sending them as {@code null} removes the value (EXT-JOB-01). */
    public static final Set<String> NULLABLE_FIELDS = Set.of("salaryMin", "salaryMax", "applicationDeadline");

    public boolean has(String field) {
        return presentFields.contains(field);
    }

    public JobPatchRequest withPresentFields(Set<String> fields) {
        return new JobPatchRequest(title, description, requiredSkills, experienceLevel, contractType, location,
                workMode, salaryMin, salaryMax, applicationDeadline, positions, Set.copyOf(fields));
    }
}
