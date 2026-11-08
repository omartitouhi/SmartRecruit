package com.smartrecruit.dashboard.dto;

/** One company's figures; a value is null when its source service could not be reached. */
public record CompanyStatsResponse(
        Long publishedJobs,
        Long applicationsReceived,
        Long acceptedApplications,
        Double acceptanceRate
) {
}
