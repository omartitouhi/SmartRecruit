package com.smartrecruit.job.dto;

import com.smartrecruit.job.model.JobStatus;
import jakarta.validation.constraints.NotNull;

/** Payload of {@code PATCH /api/v1/jobs/{id}/status} (BE-JOB-03). */
public record JobStatusRequest(@NotNull(message = "Le statut est obligatoire") JobStatus status) {
}
