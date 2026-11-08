package com.smartrecruit.job.repository;

import com.smartrecruit.job.model.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Bookmarked offers of the candidates (EXT-SAVED-01). */
public interface SavedJobRepository extends JpaRepository<SavedJob, UUID> {

    boolean existsByCandidateIdAndJobId(UUID candidateId, UUID jobId);

    long countByCandidateId(UUID candidateId);

    List<SavedJob> findByCandidateIdOrderBySavedAtDesc(UUID candidateId);

    @Query("select s.jobId from SavedJob s where s.candidateId = :candidateId and s.jobId in :jobIds")
    List<UUID> findSavedJobIds(@Param("candidateId") UUID candidateId, @Param("jobIds") Collection<UUID> jobIds);

    @Modifying
    @Query("delete from SavedJob s where s.candidateId = :candidateId and s.jobId = :jobId")
    int deleteByCandidateIdAndJobId(@Param("candidateId") UUID candidateId, @Param("jobId") UUID jobId);

    @Modifying
    @Query("delete from SavedJob s where s.candidateId = :candidateId")
    int deleteByCandidateId(@Param("candidateId") UUID candidateId);
}
