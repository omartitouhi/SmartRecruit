package com.smartrecruit.user.dto;

import com.smartrecruit.user.model.Education;
import com.smartrecruit.user.model.Experience;

import java.util.List;

/**
 * Data extracted from the candidate's last analysed CV, proposed for validation before it is saved
 * on the profile (BE-CV-02). Same shapes as the profile fields, dates as {@code YYYY-MM}.
 */
public record CvSuggestionsResponse(List<String> skills, List<Experience> experiences, List<Education> education,
                                    List<String> languages) {
}
