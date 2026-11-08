package com.smartrecruit.application.dto;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.Interview;
import com.smartrecruit.application.model.InterviewMode;

import java.time.Instant;
import java.util.UUID;

/** Element of {@code GET /api/v1/applications/interviews/upcoming} (EXT-APP-02). */
public record UpcomingInterviewResponse(UUID applicationId, UUID jobId, UUID candidateId, Instant scheduledAt,
                                        Integer durationMinutes, InterviewMode mode, String location) {

    public static UpcomingInterviewResponse from(Application application) {
        Interview interview = application.getInterview();
        return new UpcomingInterviewResponse(application.getId(), application.getJobId(), application.getCandidateId(),
                interview.getScheduledAt(), interview.getDurationMinutes(), interview.getMode(), interview.getLocation());
    }
}
