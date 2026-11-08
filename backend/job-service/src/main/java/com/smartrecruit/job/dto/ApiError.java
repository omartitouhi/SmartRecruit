package com.smartrecruit.job.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Error payload, same shape as Spring Boot's default error body ({@code DATA_MODEL.md} §1).
 * {@code fieldErrors} is only present on validation failures (400).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
