package com.smartrecruit.auth.model;

/**
 * Mirrors the {@code Role} enum documented in {@code DATA_MODEL.md} (§2).
 * Any change here must be reflected in every service that stores a role
 * (auth-service) and in the frontend / ai-service equivalents.
 */
public enum Role {
    CANDIDATE,
    RECRUITER,
    ADMIN
}
