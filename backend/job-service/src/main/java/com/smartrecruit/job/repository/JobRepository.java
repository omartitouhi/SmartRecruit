package com.smartrecruit.job.repository;

import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    interface StatusCount {
        JobStatus getStatus();

        long getTotal();
    }

    @Query("select j.status as status, count(j) as total from Job j group by j.status")
    List<StatusCount> countByStatus();

    @Query("select j.status as status, count(j) as total from Job j where j.companyId = :companyId group by j.status")
    List<StatusCount> countByStatusForCompany(@Param("companyId") UUID companyId);

    /** EXT-JOB-01: published offers whose deadline is between {@code from} and {@code to}, both included. */
    @Query("select count(j) from Job j where j.status = :status and j.applicationDeadline between :from and :to")
    long countClosingBetween(@Param("status") JobStatus status, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(j) from Job j where j.companyId = :companyId and j.status = :status"
            + " and j.applicationDeadline between :from and :to")
    long countClosingBetweenForCompany(@Param("companyId") UUID companyId, @Param("status") JobStatus status,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** EXT-JOB-01: offers still in {@code status} although their deadline is before {@code today}. */
    List<Job> findByStatusAndApplicationDeadlineBefore(JobStatus status, LocalDate today);
}
