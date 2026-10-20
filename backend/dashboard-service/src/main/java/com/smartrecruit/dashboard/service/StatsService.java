package com.smartrecruit.dashboard.service;

import com.smartrecruit.dashboard.client.ApplicationServiceClient;
import com.smartrecruit.dashboard.client.ApplicationServiceClient.ApplicationStats;
import com.smartrecruit.dashboard.client.AuthServiceClient;
import com.smartrecruit.dashboard.client.AuthServiceClient.AccountStats;
import com.smartrecruit.dashboard.client.JobServiceClient;
import com.smartrecruit.dashboard.client.JobServiceClient.JobStats;
import com.smartrecruit.dashboard.client.Upstream;
import com.smartrecruit.dashboard.dto.GlobalStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * BE-DASH-01 — figures aggregated from the other services. Each source is best effort: when one is down
 * its values are null and the rest of the response is still returned.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final AuthServiceClient authClient;
    private final JobServiceClient jobClient;
    private final ApplicationServiceClient applicationClient;

    public GlobalStatsResponse global() {
        AccountStats accounts = Upstream.orNull(authClient::stats);
        JobStats jobs = Upstream.orNull(() -> jobClient.stats(null));
        ApplicationStats applications = Upstream.orNull(() -> applicationClient.stats(null));
        return new GlobalStatsResponse(
                from(jobs, JobStats::published),
                from(applications, ApplicationStats::total),
                from(accounts, AccountStats::total),
                from(accounts, AccountStats::byRole),
                from(applications, ApplicationStats::averageMatchScore));
    }

    private static <S, T> T from(S source, Function<S, T> field) {
        return source == null ? null : field.apply(source);
    }
}
