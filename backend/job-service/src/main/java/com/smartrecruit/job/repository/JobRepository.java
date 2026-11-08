package com.smartrecruit.job.repository;

import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
