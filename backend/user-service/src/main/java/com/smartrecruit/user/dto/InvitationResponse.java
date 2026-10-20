package com.smartrecruit.user.dto;

import com.smartrecruit.user.model.CompanyInvitation;
import com.smartrecruit.user.model.InvitationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Invitation as exposed by the API; the token is never part of it.
 */
public record InvitationResponse(UUID id, String email, InvitationStatus status, Instant expiresAt, Instant createdAt) {

    public static InvitationResponse from(CompanyInvitation invitation, Instant now) {
        return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.statusAt(now),
                invitation.getExpiresAt(), invitation.getCreatedAt());
    }
}
