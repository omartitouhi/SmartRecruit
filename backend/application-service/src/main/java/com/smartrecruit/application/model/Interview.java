package com.smartrecruit.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Interview planned for an application in {@code INTERVIEW} (EXT-APP-02). Embedded in
 * {@link Application}: all columns null means "no interview" and Hibernate reads it back as null.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {

    @Column(name = "interview_scheduled_at")
    private Instant scheduledAt;

    @Column(name = "interview_duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_mode")
    private InterviewMode mode;

    /** Address or video-call link; optional. */
    @Column(name = "interview_location", length = 500)
    private String location;
}
