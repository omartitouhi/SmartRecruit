package com.smartrecruit.application.model;

import java.util.Map;
import java.util.Set;

/**
 * Mirrors the {@code ApplicationStatus} enum documented in {@code DATA_MODEL.md} (§2).
 * {@link #ALLOWED_TRANSITIONS} encodes the transition table from the same section
 * (used by {@code BE-APP-04}) — keep both in sync.
 */
public enum ApplicationStatus {
    RECEIVED,
    IN_REVIEW,
    INTERVIEW,
    ACCEPTED,
    REJECTED,
    WITHDRAWN;

    public static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            RECEIVED, Set.of(IN_REVIEW, WITHDRAWN),
            IN_REVIEW, Set.of(INTERVIEW, WITHDRAWN),
            INTERVIEW, Set.of(ACCEPTED, REJECTED),
            ACCEPTED, Set.of(),
            REJECTED, Set.of(),
            WITHDRAWN, Set.of()
    );

    public boolean canTransitionTo(ApplicationStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
