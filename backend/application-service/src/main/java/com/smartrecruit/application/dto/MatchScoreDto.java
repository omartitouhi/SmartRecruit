package com.smartrecruit.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Mirrors {@code MatchScore} in {@code DATA_MODEL.md} §4.1 — the response shape of
 * {@code POST /api/v1/ai/matching/score} in {@code ai-service}, embedded verbatim
 * into {@link com.smartrecruit.application.model.Application#getMatchScore()}.
 *
 * Any field added here must also be added to the Pydantic {@code MatchScore}
 * (ai-service/app/schemas/common.py) and to {@code MatchScore} in the frontend's
 * {@code src/types/contracts.ts} — see DATA_MODEL.md §5.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchScoreDto {

    private double score;
    private List<MatchCriterionDto> criteria;
}
