package com.smartrecruit.application.repository;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByJobIdAndCandidateIdAndStatusNot(UUID jobId, UUID candidateId, ApplicationStatus excluded);

    List<Application> findByCandidateIdOrderByAppliedAtDesc(UUID candidateId);

    List<Application> findByJobIdAndStatusNot(UUID jobId, ApplicationStatus excluded);

    /** Rows of {@code [jobId, count]} for the jobs that have at least one application of another status than {@code excluded}. */
    @Query("select a.jobId, count(a) from Application a where a.jobId in :jobIds and a.status <> :excluded group by a.jobId")
    List<Object[]> countByJob(Collection<UUID> jobIds, ApplicationStatus excluded);
}
