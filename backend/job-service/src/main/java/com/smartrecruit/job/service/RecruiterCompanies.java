package com.smartrecruit.job.service;

import com.smartrecruit.job.client.CompanyDirectoryClient;
import com.smartrecruit.job.client.CompanyDirectoryClient.CompanySummary;
import com.smartrecruit.job.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the company of a recruiter account through user-service, the only owner of that link.
 * Authorization decisions depend on it, so an unreachable user-service is a 503, not a silent default.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecruiterCompanies {

    private final CompanyDirectoryClient companyClient;

    public Optional<UUID> companyIdOf(UUID accountId) {
        try {
            return companyClient.companyOf(accountId).map(CompanySummary::id);
        } catch (RestClientException e) {
            log.warn("user-service indisponible pour retrouver l'entreprise d'un recruteur : {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service entreprises est temporairement indisponible, réessayez plus tard");
        }
    }

    /** @throws ApiException 403 if the recruiter has not created or joined a company yet */
    public UUID requireCompanyId(UUID accountId) {
        return companyIdOf(accountId).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                "Rattachez-vous d'abord à une entreprise pour gérer des offres"));
    }
}
