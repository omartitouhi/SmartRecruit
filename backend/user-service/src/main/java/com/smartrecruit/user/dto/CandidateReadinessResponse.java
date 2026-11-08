package com.smartrecruit.user.dto;

import com.smartrecruit.user.model.CandidateProfile;
import com.smartrecruit.user.model.CvAnalysisStatus;

import java.time.Instant;

/**
 * Whether a candidate can apply, as asked by application-service before accepting an application:
 * {@code profileComplete} means first name, last name and email are filled in.
 */
public record CandidateReadinessResponse(boolean profileComplete, boolean hasCv, CvAnalysisStatus cvAnalysisStatus,
                                         Instant cvParsedAt) {

    public static CandidateReadinessResponse from(CandidateProfile profile) {
        boolean complete = isFilled(profile.getFirstName()) && isFilled(profile.getLastName())
                && isFilled(profile.getEmail());
        return new CandidateReadinessResponse(complete, profile.getCvStorageKey() != null,
                profile.analysisStatus(), profile.getCvParsedAt());
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}
