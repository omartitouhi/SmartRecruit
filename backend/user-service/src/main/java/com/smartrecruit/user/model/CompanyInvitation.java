package com.smartrecruit.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Invitation of a recruiter to join a company (BE-COMP-03). Only the SHA-256 hash of the token is
 * stored; the raw token travels in the link sent to {@code email}.
 */
@Entity
@Table(name = "company_invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyInvitation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Stored as {@code PENDING} or {@code ACCEPTED} only. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant createdAt;

    /** Status as shown to API clients: a pending invitation past its expiry is {@code EXPIRED}. */
    public InvitationStatus statusAt(Instant now) {
        return status == InvitationStatus.PENDING && !expiresAt.isAfter(now) ? InvitationStatus.EXPIRED : status;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
