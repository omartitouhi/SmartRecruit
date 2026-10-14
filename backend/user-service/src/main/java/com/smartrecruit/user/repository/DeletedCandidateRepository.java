package com.smartrecruit.user.repository;

import com.smartrecruit.user.model.DeletedCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DeletedCandidateRepository extends JpaRepository<DeletedCandidate, UUID> {

    /** Deletions not yet propagated to every service (BE-SEC-02). */
    @Query("select d.id from DeletedCandidate d where d.authErasedAt is null or d.aiErasedAt is null order by d.deletedAt")
    List<UUID> findPendingErasureIds();
}
