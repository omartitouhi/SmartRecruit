package com.smartrecruit.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors {@code MatchCriterion} in {@code DATA_MODEL.md} §4.1, itself a mirror
 * of the Pydantic {@code MatchCriterion} in {@code ai-service/app/schemas/common.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchCriterionDto {

    private String label;
    private double weight;
    private double contribution;
    private String explanation;
}
