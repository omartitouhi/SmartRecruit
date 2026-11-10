package com.smartrecruit.dashboard.service;

import com.smartrecruit.dashboard.client.ApplicationServiceClient;
import com.smartrecruit.dashboard.client.ApplicationServiceClient.ApplicationStats;
import com.smartrecruit.dashboard.client.AuthServiceClient;
import com.smartrecruit.dashboard.client.AuthServiceClient.AccountStats;
import com.smartrecruit.dashboard.client.JobServiceClient;
import com.smartrecruit.dashboard.client.JobServiceClient.JobStats;
import com.smartrecruit.dashboard.client.Upstream;
import com.smartrecruit.dashboard.client.UserServiceClient;
import com.smartrecruit.dashboard.dto.CompanyStatsResponse;
import com.smartrecruit.dashboard.dto.GlobalStatsResponse;
import com.smartrecruit.dashboard.exception.ApiException;
import com.smartrecruit.dashboard.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * BE-DASH-01 / BE-DASH-02 (enriched by EXT-DASH-01) — figures aggregated from the other services. Each source is best effort: when one is down
 * its values are null and the rest of the response is still returned.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    /** Steps of the hiring funnel, in order; withdrawn applications are left out (EXT-DASH-01). */
    static final List<String> FUNNEL = List.of("RECEIVED", "IN_REVIEW", "INTERVIEW", "ACCEPTED", "REJECTED");

    private final AuthServiceClient authClient;
    private final UserServiceClient userClient;
    private final JobServiceClient jobClient;
    private final ApplicationServiceClient applicationClient;

    public GlobalStatsResponse global(LocalDate from, LocalDate to) {
        AccountStats accounts = Upstream.orNull(authClient::stats);
        JobStats jobs = Upstream.orNull(() -> jobClient.stats(null));
        ApplicationStats applications = Upstream.orNull(() -> applicationClient.stats(null, from, to));
        return new GlobalStatsResponse(
                from(jobs, JobStats::published),
                from(applications, ApplicationStats::total),
                from(accounts, AccountStats::total),
                from(accounts, AccountStats::byRole),
                from(applications, ApplicationStats::averageMatchScore),
                from(applications, StatsService::funnel),
                from(applications, ApplicationStats::averageDecisionDays),
                from(jobs, JobStats::closingSoon));
    }

    /** A recruiter only sees their own company (membership checked in user-service); admins see any. */
    public CompanyStatsResponse company(AuthenticatedUser caller, UUID companyId, LocalDate from, LocalDate to) {
        if (!"ADMIN".equals(caller.role())) {
            boolean member = Upstream.call(() -> userClient.companyOfMember(caller.id()))
                    .filter(company -> companyId.equals(company.id()))
                    .isPresent();
            if (!member) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Accès refusé : vous n'appartenez pas à cette entreprise");
            }
        }
        JobStats jobs = Upstream.orNull(() -> jobClient.stats(companyId));
        ApplicationStats applications = Upstream.orNull(() -> applicationClient.stats(companyId, from, to));
        return new CompanyStatsResponse(
                from(jobs, JobStats::published),
                from(applications, ApplicationStats::total),
                from(applications, ApplicationStats::accepted),
                from(applications, ApplicationStats::acceptanceRate),
                from(applications, StatsService::funnel),
                from(applications, ApplicationStats::averageDecisionDays),
                from(applications, ApplicationStats::upcomingInterviews),
                from(jobs, JobStats::closingSoon));
    }

    /** The five funnel steps in order, 0 for a step the source did not report. */
    private static Map<String, Long> funnel(ApplicationStats stats) {
        Map<String, Long> funnel = new LinkedHashMap<>();
        Map<String, Long> byStatus = stats.byStatus() == null ? Map.of() : stats.byStatus();
        FUNNEL.forEach(status -> funnel.put(status, byStatus.getOrDefault(status, 0L)));
        return funnel;
    }

    private static <S, T> T from(S source, Function<S, T> field) {
        return source == null ? null : field.apply(source);
    }
}
