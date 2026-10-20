package com.smartrecruit.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Values of a {@code PATCH /api/v1/users/companies/{id}} body (BE-COMP-02), JSON Merge Patch semantics
 * like {@link CandidateProfilePatchRequest}: {@link #presentFields} tells an absent field (kept) from a
 * field sent as {@code null} (cleared; rejected for name and sector).
 */
public record CompanyPatchRequest(
        @Pattern(regexp = ".*\\S.*", message = "Le nom de l'entreprise est obligatoire")
        @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
        String name,

        @Pattern(regexp = ".*\\S.*", message = "Le secteur d'activité est obligatoire")
        @Size(max = 100, message = "Le secteur ne doit pas dépasser 100 caractères")
        String sector,

        @Size(max = 5000, message = "La description ne doit pas dépasser 5000 caractères")
        String description,

        @Size(max = 500, message = "L'URL du logo ne doit pas dépasser 500 caractères")
        @Pattern(regexp = "^https?://\\S+$", message = "L'URL du logo doit commencer par http:// ou https://")
        String logoUrl,

        Set<String> presentFields
) {

    /** Fields that may not be removed from a company. */
    public static final Set<String> MANDATORY_FIELDS = Set.of("name", "sector");

    public boolean has(String field) {
        return presentFields.contains(field);
    }

    public CompanyPatchRequest withPresentFields(Set<String> fields) {
        return new CompanyPatchRequest(name, sector, description, logoUrl, Set.copyOf(fields));
    }
}
