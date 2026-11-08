package com.smartrecruit.job.service;

import com.smartrecruit.job.client.AiRecommendationClient;
import com.smartrecruit.job.client.AiRecommendationClient.AiRecommendation;
import com.smartrecruit.job.client.CompanyDirectoryClient;
import com.smartrecruit.job.dto.RecommendedJobResponse;
import com.smartrecruit.job.exception.ApiException;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BE-REC-01: joins the AI recommendations (job id + score) with the offer details and the company
 * name, so the frontend gets a list ready to display.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final AiRecommendationClient aiClient;
    private final CompanyDirectoryClient companyClient;
    private final JobRepository jobRepository;

    /**
     * @return at most {@code limit} published offers, best score first
     */
    @Transactional(readOnly = true)
    public List<RecommendedJobResponse> recommendFor(UUID candidateId, int limit) {
        List<AiRecommendation> recommendations;
        try {
            recommendations = aiClient.recommendJobs(candidateId);
        } catch (RestClientException e) {
            log.warn("ai-service indisponible pour les recommandations du candidat {} : {}", candidateId, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Les recommandations sont temporairement indisponibles, réessayez plus tard");
        }

        // Best score per job, in case the AI returns a job twice.
        Map<UUID, Double> scoreByJob = recommendations.stream()
                .filter(recommendation -> recommendation.jobId() != null)
                .collect(Collectors.toMap(AiRecommendation::jobId, AiRecommendation::score, Math::max, LinkedHashMap::new));
        Map<UUID, Job> publishedJobs = jobRepository.findAllById(scoreByJob.keySet()).stream()
                .filter(job -> job.getStatus() == JobStatus.PUBLISHED)
                .collect(Collectors.toMap(Job::getId, Function.identity()));

        List<Job> selected = scoreByJob.entrySet().stream()
                .filter(entry -> publishedJobs.containsKey(entry.getKey()))
                .sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(entry -> publishedJobs.get(entry.getKey()))
                .toList();

        Map<UUID, String> companyNames = companyNames(selected);
        return selected.stream()
                .map(job -> RecommendedJobResponse.of(job, companyNames.get(job.getCompanyId()), scoreByJob.get(job.getId())))
                .toList();
    }

    /** Company names are a nice-to-have: without user-service the list is still returned. */
    private Map<UUID, String> companyNames(List<Job> jobs) {
        Set<UUID> companyIds = jobs.stream().map(Job::getCompanyId).filter(Objects::nonNull).collect(Collectors.toSet());
        try {
            return companyClient.namesOf(companyIds);
        } catch (RestClientException e) {
            log.warn("user-service indisponible, recommandations renvoyées sans nom d'entreprise : {}", e.getMessage());
            return Map.of();
        }
    }
}
