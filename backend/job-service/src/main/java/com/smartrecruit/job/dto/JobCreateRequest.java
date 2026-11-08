package com.smartrecruit.job.dto;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import com.smartrecruit.job.model.WorkMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload of {@code POST /api/v1/jobs} (BE-JOB-01). The company is the recruiter's, resolved
 * server-side, and the offer is always published: neither {@code companyId} nor {@code status}
 * is read from the client.
 * <p>
 * EXT-JOB-01 adds optional fields: {@code workMode} (default ON_SITE), a salary range, a deadline
 * (today or later, checked by the service with its clock) and {@code positions} (default 1).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobCreateRequest {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 255, message = "Le titre ne doit pas dépasser 255 caractères")
    private String title;

    @NotBlank(message = "La description est obligatoire")
    @Size(min = 20, max = 20000, message = "La description doit contenir entre 20 et 20000 caractères")
    private String description;

    @NotNull(message = "Les compétences requises sont obligatoires")
    @Size(min = 1, max = 50, message = "Entre 1 et 50 compétences requises")
    private List<@NotBlank(message = "Une compétence ne peut pas être vide")
    @Size(max = 100, message = "Une compétence ne doit pas dépasser 100 caractères") String> requiredSkills;

    @NotNull(message = "Le niveau d'expérience est obligatoire")
    private ExperienceLevel experienceLevel;

    @NotNull(message = "Le type de contrat est obligatoire")
    private ContractType contractType;

    @NotBlank(message = "La localisation est obligatoire")
    @Size(max = 255, message = "La localisation ne doit pas dépasser 255 caractères")
    private String location;

    private WorkMode workMode;

    @Min(value = 0, message = "Le salaire minimum doit être positif")
    @Max(value = 1_000_000, message = "Le salaire minimum ne doit pas dépasser 1 000 000")
    private Integer salaryMin;

    @Min(value = 0, message = "Le salaire maximum doit être positif")
    @Max(value = 1_000_000, message = "Le salaire maximum ne doit pas dépasser 1 000 000")
    private Integer salaryMax;

    private LocalDate applicationDeadline;

    @Min(value = 1, message = "Il faut au moins un poste")
    @Max(value = 100, message = "100 postes au plus")
    private Integer positions;
}
