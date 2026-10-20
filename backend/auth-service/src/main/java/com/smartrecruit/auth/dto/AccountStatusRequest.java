package com.smartrecruit.auth.dto;

import com.smartrecruit.auth.model.AccountStatus;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /internal/v1/accounts/{id}/status}; only ACTIVE and DISABLED are accepted. */
public record AccountStatusRequest(
        @NotNull(message = "Le statut est obligatoire")
        AccountStatus status
) {
}
