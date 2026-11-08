package com.smartrecruit.user.repository;

import com.smartrecruit.user.model.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {

    /**
     * Marks the CV as analysed (DONE, with {@code cvParsedAt}) only if {@code storageKey} is still the
     * current one; returns rows updated.
     */
    @Modifying
    @Query("update CandidateProfile p set p.cvParsedAt = :at, p.cvAnalysisStatus = com.smartrecruit.user.model.CvAnalysisStatus.DONE "
            + "where p.id = :id and p.cvStorageKey = :key")
    int markParsed(@Param("id") UUID id, @Param("key") String storageKey, @Param("at") Instant at);

    /** Marks the analysis of the CV stored under {@code storageKey} as FAILED, unless it was replaced since. */
    @Modifying
    @Query("update CandidateProfile p set p.cvAnalysisStatus = com.smartrecruit.user.model.CvAnalysisStatus.FAILED "
            + "where p.id = :id and p.cvStorageKey = :key")
    int markAnalysisFailed(@Param("id") UUID id, @Param("key") String storageKey);
}
