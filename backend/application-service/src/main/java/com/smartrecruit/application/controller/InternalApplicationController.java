package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.ApplicationStatsResponse;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final ApplicationRepository applications;

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

    /** Statistics over every application, or only those of {@code companyId} when given. */
    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ApplicationStatsResponse stats(@RequestParam(required = false) UUID companyId) {
        return ApplicationStatsResponse.from(companyId == null
                ? applications.statusAndScore(ApplicationStatus.WITHDRAWN)
                : applications.statusAndScoreOfCompany(companyId, ApplicationStatus.WITHDRAWN));
    }
}
