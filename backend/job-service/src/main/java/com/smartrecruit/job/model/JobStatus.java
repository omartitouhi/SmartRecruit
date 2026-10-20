package com.smartrecruit.job.model;

/**
 * Mirrors the {@code JobStatus} enum documented in {@code DATA_MODEL.md} (§2).
 */
public enum JobStatus {
    DRAFT,
    PUBLISHED,
    CLOSED,
    MODERATED;

    /**
     * Transitions a recruiter may request: publish a draft, close a published offer, reopen a closed
     * one. {@code MODERATED} is only entered through moderation and never left.
     */
    public boolean canBecome(JobStatus next) {
        return switch (this) {
            case DRAFT -> next == PUBLISHED;
            case PUBLISHED -> next == CLOSED;
            case CLOSED -> next == PUBLISHED;
            case MODERATED -> false;
        };
    }
}
