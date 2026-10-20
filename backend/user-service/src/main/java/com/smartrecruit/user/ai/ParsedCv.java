package com.smartrecruit.user.ai;

import java.util.List;

/**
 * Stored parsing result of a CV as returned by ai-service ({@code CVParsingResult}, DATA_MODEL.md §4.2).
 * The AI sends full dates ({@code YYYY-MM-DD}); they are converted to {@code YYYY-MM} by the service.
 */
public record ParsedCv(List<String> skills, List<Experience> experiences, List<Education> education,
                       List<String> languages) {

    public record Experience(String title, String company, String startDate, String endDate, String description) {
    }

    public record Education(String degree, String institution, Integer year) {
    }
}
