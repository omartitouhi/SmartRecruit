package com.smartrecruit.application.dto;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.StatusHistoryEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public static ApplicationResponse from(Application application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .jobId(application.getJobId())
                .candidateId(application.getCandidateId())
                .status(application.getStatus())
                .matchScore(application.getMatchScore())
                .appliedAt(application.getAppliedAt())
                .statusHistory(application.getStatusHistory())
                .build();
    }
}
