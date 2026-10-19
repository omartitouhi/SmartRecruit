package com.smartrecruit.user.repository;

import com.smartrecruit.user.model.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {

    /** Marks the CV as analysed only if {@code storageKey} is still the current one; returns rows updated. */
    @Modifying
    @Query("update CandidateProfile p set p.cvParsedAt = :at where p.id = :id and p.cvStorageKey = :key")
    int markParsed(@Param("id") UUID id, @Param("key") String storageKey, @Param("at") Instant at);
}
