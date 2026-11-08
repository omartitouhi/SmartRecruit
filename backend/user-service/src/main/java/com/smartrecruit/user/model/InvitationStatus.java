package com.smartrecruit.user.model;

/**
 * Status of a company invitation as exposed by the API. {@code EXPIRED} is never stored: it is
 * derived from {@code expiresAt} (see {@link CompanyInvitation#statusAt}).
 */
public enum InvitationStatus {
    PENDING, ACCEPTED, EXPIRED
}
