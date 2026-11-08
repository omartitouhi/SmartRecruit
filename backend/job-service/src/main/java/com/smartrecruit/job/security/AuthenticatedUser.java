package com.smartrecruit.job.security;

import java.util.UUID;

/**
 * Identity extracted from the access token, available in controllers through
 * {@code @AuthenticationPrincipal AuthenticatedUser user}.
 *
 * @param id   account id ({@code sub} claim)
 * @param role {@code CANDIDATE}, {@code RECRUITER} or {@code ADMIN} ({@code DATA_MODEL.md} §2)
 */
public record AuthenticatedUser(UUID id, String role) {
}
