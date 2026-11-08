package com.smartrecruit.application.dto;

import com.smartrecruit.application.model.ApplicationStatus;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Counters over the non-withdrawn applications. {@code decided} = ACCEPTED + REJECTED and
 * {@code acceptanceRate} = accepted / decided as a 0..1 ratio, null while nothing is decided.
 * {@code averageDecisionDays} (null while nothing is decided) and {@code upcomingInterviews} come from EXT-DASH-01.
 */
public record ApplicationStatsResponse(long total, Map<ApplicationStatus, Long> byStatus, Double averageMatchScore,
                                       long accepted, long decided, Double acceptanceRate,
                                       Double averageDecisionDays, long upcomingInterviews) {

    /** @param rows {@code [ApplicationStatus, MatchScoreDto|null]} rows of non-withdrawn applications */
    public static ApplicationStatsResponse from(List<Object[]> rows, Double averageDecisionDays, long upcomingInterviews) {
        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            if (status != ApplicationStatus.WITHDRAWN) {
                byStatus.put(status, 0L);
            }
        }
        rows.forEach(row -> byStatus.merge((ApplicationStatus) row[0], 1L, Long::sum));
        Double average = rows.stream().map(row -> (MatchScoreDto) row[1]).filter(Objects::nonNull)
                .mapToDouble(MatchScoreDto::getScore).average().stream().boxed().findFirst().orElse(null);
        long accepted = byStatus.get(ApplicationStatus.ACCEPTED);
        long decided = accepted + byStatus.get(ApplicationStatus.REJECTED);
        return new ApplicationStatsResponse(rows.size(), byStatus, average, accepted, decided,
                decided == 0 ? null : (double) accepted / decided, averageDecisionDays, upcomingInterviews);
    }
}
