package com.smartrecruit.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.YearMonth;

/**
 * Mirrors the {@code Experience} object in {@code DATA_MODEL.md} §3.2
 * (embedded in {@link CandidateProfile#getExperiences()}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Experience {

    private String title;
    private String company;

    @Column(name = "start_date")
    private YearMonth startDate;

    @Column(name = "end_date")
    private YearMonth endDate;

    @Column(length = 2000)
    private String description;
}
