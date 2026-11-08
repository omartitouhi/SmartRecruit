package com.smartrecruit.user.service;

import com.smartrecruit.user.ai.CvAnalysisClient;
import com.smartrecruit.user.ai.ParsedCv;
import com.smartrecruit.user.dto.CvSuggestionsResponse;
import com.smartrecruit.user.exception.ApiException;
import com.smartrecruit.user.model.Education;
import com.smartrecruit.user.model.Experience;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Proposes the data the AI extracted from the candidate's CV (BE-CV-02). Nothing is written to the
 * profile: the candidate validates and saves it with PUT/PATCH {@code /candidates/me}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvSuggestionsService {

    private final CvAnalysisClient aiClient;

    /** 404 if no CV was analysed for the candidate, 503 if ai-service cannot be reached. */
    public CvSuggestionsResponse suggestionsFor(UUID candidateId) {
        ParsedCv parsed;
        try {
            parsed = aiClient.findParsedCv(candidateId).orElse(null);
        } catch (RestClientException e) {
            log.warn("Suggestions de CV du candidat {} : ai-service indisponible ({})", candidateId, e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Le service d'analyse de CV est momentanément indisponible");
        }
        if (parsed == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Aucun CV analysé pour ce candidat");
        }
        return new CvSuggestionsResponse(
                CandidateProfileService.nullToEmpty(parsed.skills()),
                CandidateProfileService.nullToEmpty(parsed.experiences()).stream().map(CvSuggestionsService::toExperience).toList(),
                CandidateProfileService.nullToEmpty(parsed.education()).stream().map(CvSuggestionsService::toEducation).toList(),
                CandidateProfileService.nullToEmpty(parsed.languages()));
    }

    private static Experience toExperience(ParsedCv.Experience experience) {
        return Experience.builder()
                .title(experience.title())
                .company(experience.company())
                .startDate(toYearMonth(experience.startDate()))
                .endDate(toYearMonth(experience.endDate()))
                .description(experience.description())
                .build();
    }

    private static Education toEducation(ParsedCv.Education education) {
        return Education.builder()
                .degree(education.degree())
                .institution(education.institution())
                .year(education.year())
                .build();
    }

    /**
     * {@code YYYY-MM-DD} (what the AI sends) to {@code YYYY-MM}; {@code YYYY-MM} is accepted as is.
     * A missing or unreadable date gives null: the candidate fills it in during validation.
     */
    static YearMonth toYearMonth(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            String value = date.trim();
            return value.length() > 7 ? YearMonth.from(LocalDate.parse(value)) : YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
