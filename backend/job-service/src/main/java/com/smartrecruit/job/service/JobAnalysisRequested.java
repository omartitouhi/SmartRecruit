package com.smartrecruit.job.service;

import java.util.UUID;

/**
 * Published inside the transaction that creates or changes an offer; the AI analysis only starts
 * once it has committed.
 */
public record JobAnalysisRequested(UUID jobId, String title, String description) {
}
