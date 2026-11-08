package com.smartrecruit.application.repository;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
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

    /** Rows of {@code [status, matchScore]} of every application other than {@code excluded}. */
    @Query("select a.status, a.matchScore from Application a where a.status <> :excluded")
    List<Object[]> statusAndScore(ApplicationStatus excluded);

    @Query("select a.status, a.matchScore from Application a where a.companyId = :companyId and a.status <> :excluded")
    List<Object[]> statusAndScoreOfCompany(UUID companyId, ApplicationStatus excluded);

    /** Applications in one of {@code statuses}, with their history (EXT-DASH-01 decision delay). */
    List<Application> findByStatusIn(Collection<ApplicationStatus> statuses);

    List<Application> findByCompanyIdAndStatusIn(UUID companyId, Collection<ApplicationStatus> statuses);

    /** Interviews of a company from {@code from} on, soonest first (EXT-APP-02). */
    @Query("select a from Application a where a.companyId = :companyId and a.status = :status "
            + "and a.interview.scheduledAt >= :from order by a.interview.scheduledAt asc, a.id asc")
    List<Application> upcomingInterviews(UUID companyId, ApplicationStatus status, Instant from, Limit limit);

    @Query("select count(a) from Application a where a.status = :status and a.interview.scheduledAt >= :from")
    long countUpcomingInterviews(ApplicationStatus status, Instant from);

    @Query("select count(a) from Application a where a.companyId = :companyId and a.status = :status "
            + "and a.interview.scheduledAt >= :from")
    long countUpcomingInterviewsOfCompany(UUID companyId, ApplicationStatus status, Instant from);

    List<Application> findByCandidateId(UUID candidateId);

    /** Right to be forgotten (EXT-APP-01): the cover letters of a deleted candidate. */
    @Modifying
    @Query("update Application a set a.coverLetter = null where a.candidateId = :candidateId and a.coverLetter is not null")
    int clearCoverLetters(UUID candidateId);
}
