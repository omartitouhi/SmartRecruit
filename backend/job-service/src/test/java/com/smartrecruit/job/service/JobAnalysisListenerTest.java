package com.smartrecruit.job.service;

import com.smartrecruit.job.client.AiJobAnalysisClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Reliability: an analysis requested while ai-service is down is not lost but retried once it is back. */
class JobAnalysisListenerTest {

    private final AiJobAnalysisClient ai = mock(AiJobAnalysisClient.class);
    private final JobAnalysisListener listener = new JobAnalysisListener(ai);
    private final JobAnalysisRequested event = new JobAnalysisRequested(UUID.randomUUID(), "Dev Java", "Spring Boot et PostgreSQL");

    @Test
    void failedAnalysisIsRetriedUntilTheAiAnswers() {
        doThrow(new ResourceAccessException("ai-service down")).when(ai).analyze(event.jobId(), event.title(), event.description());
        listener.analyze(event);
        listener.retryPending();
        assertThat(listener.pendingCount()).isEqualTo(1);

        doNothing().when(ai).analyze(event.jobId(), event.title(), event.description());
        listener.retryPending();
        assertThat(listener.pendingCount()).isZero();

        listener.retryPending();
        verify(ai, times(3)).analyze(event.jobId(), event.title(), event.description());
    }

    @Test
    void successfulAnalysisIsNotKept() {
        listener.analyze(event);
        assertThat(listener.pendingCount()).isZero();
    }
}
