package com.smartrecruit.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/users/companies} (BE-COMP-01).
 */
public record CompanyRequest(
        @NotBlank(message = "Le nom de l'entreprise est obligatoire")
        @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
        String name,

        @NotBlank(message = "Le secteur d'activité est obligatoire")
        @Size(max = 100, message = "Le secteur ne doit pas dépasser 100 caractères")
        String sector,

        @Size(max = 5000, message = "La description ne doit pas dépasser 5000 caractères")
        String description,

        @Size(max = 500, message = "L'URL du logo ne doit pas dépasser 500 caractères")
        @Pattern(regexp = "^https?://\S+$", message = "L'URL du logo doit commencer par http:// ou https://")
        String logoUrl
) {
}
