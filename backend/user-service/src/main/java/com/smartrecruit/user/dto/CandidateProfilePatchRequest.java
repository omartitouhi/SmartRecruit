package com.smartrecruit.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/**
 * Values of a {@code PATCH /api/v1/users/candidates/me} body (BE-USER-03). A {@code null} here can mean
 * "absent" or "sent as null": {@link #presentFields} tells them apart (JSON Merge Patch semantics):
 * <ul>
 *     <li>absent field → value kept;</li>
 *     <li>field sent as {@code null} → value cleared (rejected for firstName, lastName, email);</li>
 *     <li>field sent with a value → value replaced (lists are replaced as a whole).</li>
 * </ul>
 */
public record CandidateProfilePatchRequest(
        @Pattern(regexp = ".*\\S.*", message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
        String firstName,

        @Pattern(regexp = ".*\\S.*", message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String lastName,

        @Email(message = "L'email n'est pas valide")
        @Pattern(regexp = ".*\\S.*", message = "L'email est obligatoire")
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
        List<@NotNull(message = "Expérience vide") @Valid ExperienceRequest> experiences,

        @Size(max = 30, message = "30 formations maximum")
        List<@NotNull(message = "Formation vide") @Valid EducationRequest> education,

        @Size(max = 30, message = "30 langues maximum")
        List<@NotBlank(message = "Une langue ne peut pas être vide")
        @Size(max = 50, message = "Une langue ne doit pas dépasser 50 caractères") String> languages,

        Set<String> presentFields
) {

    /** Fields that may not be removed from a profile. */
    public static final Set<String> MANDATORY_FIELDS = Set.of("firstName", "lastName", "email");

    public boolean has(String field) {
        return presentFields.contains(field);
    }

    public CandidateProfilePatchRequest withPresentFields(Set<String> fields) {
        return new CandidateProfilePatchRequest(firstName, lastName, email, phone, location, skills, experiences,
                education, languages, Set.copyOf(fields));
    }
}
