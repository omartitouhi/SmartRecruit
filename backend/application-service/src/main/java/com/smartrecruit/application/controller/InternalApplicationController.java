package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.ApplicationStatsResponse;
import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.StatusHistoryEntry;
import com.smartrecruit.application.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * Backend-to-backend endpoints. {@code /internal/**} is not routed by the api-gateway and requires
 * a signed {@code SERVICE} identity.
 */
@RestController
@RequestMapping("/internal/v1/applications")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class InternalApplicationController {

    private static final Set<ApplicationStatus> DECIDED = Set.of(ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED);
    private static final double SECONDS_PER_DAY = 86_400d;

    private final ApplicationRepository applications;
    private final Clock clock;

    /** Applications per job, withdrawn ones excluded; every requested job is present (0 when none). */
    @GetMapping("/counts")
    @Transactional(readOnly = true)
    public Map<UUID, Long> counts(@RequestParam List<UUID> jobIds) {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        jobIds.forEach(jobId -> counts.put(jobId, 0L));
        if (!jobIds.isEmpty()) {
            applications.countByJob(jobIds, ApplicationStatus.WITHDRAWN)
                    .forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
        }
        return counts;
    }

    /**
     * Statistics over every application, or only those of {@code companyId} when given; with the decision delay
     * and the number of upcoming interviews (EXT-DASH-01).
     */
    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ApplicationStatsResponse stats(@RequestParam(required = false) UUID companyId) {
        Instant now = clock.instant();
        if (companyId == null) {
            return ApplicationStatsResponse.from(applications.statusAndScore(ApplicationStatus.WITHDRAWN),
                    averageDecisionDays(applications.findByStatusIn(DECIDED)),
                    applications.countUpcomingInterviews(ApplicationStatus.INTERVIEW, now));
        }
        return ApplicationStatsResponse.from(applications.statusAndScoreOfCompany(companyId, ApplicationStatus.WITHDRAWN),
                averageDecisionDays(applications.findByCompanyIdAndStatusIn(companyId, DECIDED)),
                applications.countUpcomingInterviewsOfCompany(companyId, ApplicationStatus.INTERVIEW, now));
    }

    /** Mean days from {@code appliedAt} to the first ACCEPTED/REJECTED history entry, 1 decimal; null when none. */
    static Double averageDecisionDays(List<Application> decided) {
        OptionalDouble average = decided.stream()
                .map(InternalApplicationController::decisionDays)
                .flatMap(Optional::stream)
                .mapToDouble(Double::doubleValue)
                .average();
        return average.isPresent() ? Math.round(average.getAsDouble() * 10) / 10.0 : null;
    }

    private static Optional<Double> decisionDays(Application application) {
        if (application.getAppliedAt() == null) {
            return Optional.empty();
        }
        return application.getStatusHistory().stream()
                .filter(entry -> DECIDED.contains(entry.getStatus()))
                .map(StatusHistoryEntry::getChangedAt)
                .filter(Objects::nonNull)
                .findFirst()
                .map(decidedAt -> Duration.between(application.getAppliedAt(), decidedAt).toSeconds() / SECONDS_PER_DAY);
    }
}
