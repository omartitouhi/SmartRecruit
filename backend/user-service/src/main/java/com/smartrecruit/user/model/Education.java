package com.smartrecruit.user.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors the {@code Education} object in {@code DATA_MODEL.md} §3.2
 * (embedded in {@link CandidateProfile#getEducation()}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Education {

    private String degree;
    private String institution;
    private Integer year;
}
