package com.smartrecruit.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

/** Account as returned by auth-service ({@code role} and {@code status} stay plain strings). */
public record AccountResponse(UUID id, String email, String role, String status, Instant createdAt,
                              Instant updatedAt) {
}
