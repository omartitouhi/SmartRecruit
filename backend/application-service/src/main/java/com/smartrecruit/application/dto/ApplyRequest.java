package com.smartrecruit.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ApplyRequest(@NotNull(message = "jobId est obligatoire") UUID jobId) {
}
