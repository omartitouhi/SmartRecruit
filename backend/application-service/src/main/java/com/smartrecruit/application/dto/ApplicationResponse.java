package com.smartrecruit.application.dto;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.Interview;
import com.smartrecruit.application.model.StatusHistoryEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code Application} of {@code DATA_MODEL.md} §3.4, with the cover letter, history messages and interview
 * (EXT-APP-01/02). {@code notesCount} and {@code averageRating} (EXT-APP-03) are only filled in the views of the
 * hiring team; they stay null in what the candidate receives.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationResponse {

    private UUID id;
    private UUID jobId;
    private UUID candidateId;
    private ApplicationStatus status;
    private MatchScoreDto matchScore;
    private Instant appliedAt;
    private List<StatusHistoryEntry> statusHistory;
    private String coverLetter;
    private Interview interview;
    private Long notesCount;
    private Double averageRating;

    /** Candidate view: no team notes. */
    public static ApplicationResponse from(Application application) {
        return base(application).build();
    }

    /** Hiring team view (recruiters of the company, admins): with the notes summary. */
    public static ApplicationResponse forTeam(Application application, NoteSummary notes) {
        return base(application).notesCount(notes.count()).averageRating(notes.averageRating()).build();
    }

    private static ApplicationResponseBuilder base(Application application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .jobId(application.getJobId())
                .candidateId(application.getCandidateId())
                .status(application.getStatus())
                .matchScore(application.getMatchScore())
                .appliedAt(application.getAppliedAt())
                .statusHistory(application.getStatusHistory())
                .coverLetter(application.getCoverLetter())
                .interview(application.getInterview());
    }
}
