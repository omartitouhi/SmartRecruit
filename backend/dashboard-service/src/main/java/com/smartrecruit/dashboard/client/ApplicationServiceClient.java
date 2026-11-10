package com.smartrecruit.dashboard.client;

import com.smartrecruit.dashboard.security.InternalIdentityVerifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** application-service internals ({@code /internal/v1/applications}). Calls throw RestClientException on failure. */
@Component
public class ApplicationServiceClient {

    /**
     * {@code averageMatchScore}, {@code acceptanceRate} and {@code averageDecisionDays} are null when nothing can be
     * averaged yet; {@code averageDecisionDays} and {@code upcomingInterviews} come from EXT-DASH-01.
     */
    public record ApplicationStats(long total, Map<String, Long> byStatus, Double averageMatchScore, long accepted,
                                   long decided, Double acceptanceRate, Double averageDecisionDays,
                                   Long upcomingInterviews) {
    }

    private final RestClient client;

    public ApplicationServiceClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer,
                                    Clock clock) {
        this.client = ServiceRestClients.create(endpoints.applicationServiceUri(), signer, clock);
    }

    /** @param companyId restricts the statistics to one company; null means platform-wide; from/to: application dates, inclusive */
    public ApplicationStats stats(UUID companyId, LocalDate from, LocalDate to) {
        return client.get()
                .uri(uri -> {
                    uri.path("/internal/v1/applications/stats");
                    if (companyId != null) {
                        uri.queryParam("companyId", companyId);
                    }
                    if (from != null) {
                        uri.queryParam("from", from);
                    }
                    if (to != null) {
                        uri.queryParam("to", to);
                    }
                    return uri.build();
                })
                .retrieve()
                .body(ApplicationStats.class);
    }
}
