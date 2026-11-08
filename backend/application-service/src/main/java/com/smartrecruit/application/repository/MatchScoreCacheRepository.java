package com.smartrecruit.application.repository;

import com.smartrecruit.application.model.MatchScoreCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchScoreCacheRepository extends JpaRepository<MatchScoreCacheEntry, UUID> {

    Optional<MatchScoreCacheEntry> findByCandidateIdAndJobId(UUID candidateId, UUID jobId);
}
