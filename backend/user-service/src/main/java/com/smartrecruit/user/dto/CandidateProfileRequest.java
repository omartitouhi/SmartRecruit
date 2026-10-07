package com.smartrecruit.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code PUT /api/v1/users/candidates/me} (BE-USER-01): full profile, replaces the stored one.
 * {@code cvUrl} / {@code cvParsedAt} are managed by the CV upload and AI analysis, not by this request.
 */
public record CandidateProfileRequest(
        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String lastName,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email n'est pas valide")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères")
        String email,

        @Pattern(regexp = "^\\+?[0-9 .()-]{6,20}$", message = "Le numéro de téléphone n'est pas valide")
        String phone,

        @Size(max = 100, message = "La ville ne doit pas dépasser 100 caractères")
        String location,

        @Size(max = 100, message = "100 compétences maximum")
        List<@NotBlank(message = "Une compétence ne peut pas être vide")
        @Size(max = 100, message = "Une compétence ne doit pas dépasser 100 caractères") String> skills,

        @Size(max = 50, message = "50 expériences maximum")
        List<@Valid ExperienceRequest> experiences,

        @Size(max = 30, message = "30 formations maximum")
        List<@Valid EducationRequest> education,

        @Size(max = 30, message = "30 langues maximum")
        List<@NotBlank(message = "Une langue ne peut pas être vide")
        @Size(max = 50, message = "Une langue ne doit pas dépasser 50 caractères") String> languages
) {
}
