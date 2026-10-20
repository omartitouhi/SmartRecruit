package com.smartrecruit.application.dto;

import com.smartrecruit.application.model.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull(message = "status est obligatoire") ApplicationStatus status) {
}
