package com.smartrecruit.dashboard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Body of {@code PATCH /api/v1/dashboard/admin/users/{id}/status}. */
public record AccountStatusRequest(
        @NotNull(message = "Le statut est obligatoire")
        @Pattern(regexp = "ACTIVE|DISABLED", message = "Le statut doit être ACTIVE ou DISABLED")
        String status
) {
}
