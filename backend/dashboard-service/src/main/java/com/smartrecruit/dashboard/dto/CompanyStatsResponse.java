package com.smartrecruit.dashboard.dto;

import java.util.Map;

/**
 * One company's figures; a value is null when its source service could not be reached.
 * {@code applicationsByStatus} (the hiring funnel), {@code averageDecisionDays}, {@code upcomingInterviews} and
 * {@code jobsClosingSoon} come from EXT-DASH-01.
 */
public record CompanyStatsResponse(
        Long publishedJobs,
        Long applicationsReceived,
        Long acceptedApplications,
        Double acceptanceRate,
        Map<String, Long> applicationsByStatus,
        Double averageDecisionDays,
        Long upcomingInterviews,
        Long jobsClosingSoon
) {
}
