package com.smartrecruit.application.dto;

/**
 * Team notes of one application as shown in the recruiter list (EXT-APP-03): how many, and the mean of the
 * rated ones (1 decimal, null when none is rated).
 */
public record NoteSummary(long count, Double averageRating) {

    public static final NoteSummary NONE = new NoteSummary(0, null);
}
