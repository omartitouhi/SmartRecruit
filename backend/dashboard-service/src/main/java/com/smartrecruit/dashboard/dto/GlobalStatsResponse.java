package com.smartrecruit.dashboard.dto;

import java.util.Map;

/**
 * Platform-wide figures; a value is null when its source service could not be reached.
 * {@code applicationsByStatus}, {@code averageDecisionDays} and {@code jobsClosingSoon} come from EXT-DASH-01.
 */
public record GlobalStatsResponse(
        Long activeJobs,
        Long totalApplications,
        Long totalUsers,
        Map<String, Long> usersByRole,
        Double averageMatchScore,
        Map<String, Long> applicationsByStatus,
        Double averageDecisionDays,
        Long jobsClosingSoon
) {
}
