package com.smartrecruit.user.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Tombstone of a candidate who exercised the right to be forgotten (BE-USER-04). It holds no
 * personal data: only the account id, so references from other services (applications) can be
 * displayed as "compte supprimé" and the profile cannot be silently recreated.
 */
@Entity
@Table(name = "deleted_candidates")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeletedCandidate {

    @Id
    private UUID id;

    private Instant deletedAt;
}
