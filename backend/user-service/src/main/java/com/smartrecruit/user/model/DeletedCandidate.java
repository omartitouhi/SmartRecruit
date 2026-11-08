package com.smartrecruit.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Tombstone of a candidate who exercised the right to be forgotten (BE-USER-04). It holds no
 * personal data: only the account id, so references from other services (applications) can be
 * displayed as "compte supprimé" and the profile cannot be silently recreated.
 * <p>
 * It also tracks the propagation of the erasure to the other services holding the candidate's
 * data (BE-SEC-02), which must be done before {@link #eraseDeadline}.
 */
@Entity
@Table(name = "deleted_candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletedCandidate {

    @Id
    private UUID id;

    private Instant deletedAt;

    /** Legal limit to erase the data everywhere: deletion date + 30 days. */
    private Instant eraseDeadline;

    /** Account anonymized in auth-service. */
    private Instant authErasedAt;

    /** CV analysis / matching data removed from ai-service. */
    private Instant aiErasedAt;

    /** Saved offers removed from job-service (EXT-SAVED-01). */
    private Instant jobsErasedAt;

    /** Cover letters and recruiter notes removed from application-service (EXT-APP-01, EXT-APP-03). */
    private Instant applicationsErasedAt;

    private int erasureAttempts;

    private Instant lastErasureAttemptAt;

    @Column(length = 500)
    private String lastErasureError;

    @Version
    private Long version;

    public boolean isFullyErased() {
        return authErasedAt != null && aiErasedAt != null && jobsErasedAt != null && applicationsErasedAt != null;
    }
}
