package com.smartrecruit.application.repository;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByJobIdAndCandidateIdAndStatusNot(UUID jobId, UUID candidateId, ApplicationStatus excluded);

    List<Application> findByCandidateIdOrderByAppliedAtDesc(UUID candidateId);

    List<Application> findByJobIdAndStatusNot(UUID jobId, ApplicationStatus excluded);
}
