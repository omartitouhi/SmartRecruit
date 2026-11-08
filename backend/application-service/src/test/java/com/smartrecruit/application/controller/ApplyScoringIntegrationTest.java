package com.smartrecruit.application.controller;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.support.ApiTestSupport;
import com.smartrecruit.application.support.FakeServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-MATCH-01 — the score is computed in the background after the application is committed. */
class ApplyScoringIntegrationTest extends ApiTestSupport {

    private final UUID candidateId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final Instant parsedAt = Instant.parse("2026-01-02T10:00:00Z");

    @BeforeEach
    void happyStubs() {
        stubJob(jobId, UUID.randomUUID(), "PUBLISHED", Instant.parse("2026-01-01T10:00:00Z"));
        stubReadiness(candidateId, true, true, parsedAt);
    }

    private void apply() throws Exception {
        mockMvc.perform(as(post("/api/v1/applications"), candidateId, "CANDIDATE")
                .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":\"%s\"}".formatted(jobId)))
                .andExpect(status().isCreated());
    }

    private Application onlyApplication() {
        return applications.findAll().getFirst();
    }

    @Test
    void scoreIsStoredOnTheApplicationAfterwards() throws Exception {
        stubScore(74.5);

        apply();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Application stored = onlyApplication();
            assertThat(stored.getMatchScore()).isNotNull();
            assertThat(stored.getMatchScore().getScore()).isEqualTo(74.5);
            assertThat(stored.getMatchScore().getCriteria()).hasSize(1);
        });
        assertThat(onlyApplication().getStatus()).isEqualTo(ApplicationStatus.RECEIVED);
        assertThat(onlyApplication().getStatusHistory()).hasSize(1);
        assertThat(scoreCache.count()).isEqualTo(1);
    }

    @Test
    void aiFailureLeavesTheApplicationWithoutScore() throws Exception {
        FakeServices.on(AI_SCORE, 500, "");

        apply();

        await().atMost(Duration.ofSeconds(10)).until(() -> !FakeServices.calls(AI_SCORE).isEmpty());
        Thread.sleep(300);
        assertThat(onlyApplication().getMatchScore()).isNull();
        assertThat(onlyApplication().getStatus()).isEqualTo(ApplicationStatus.RECEIVED);
        assertThat(scoreCache.count()).isZero();
    }

    @Test
    void cachedScoreIsReusedWhenApplyingAgainAfterWithdrawal() throws Exception {
        stubScore(60);
        apply();
        await().atMost(Duration.ofSeconds(10)).until(() -> onlyApplication().getMatchScore() != null);
        Application first = onlyApplication();
        first.setStatus(ApplicationStatus.WITHDRAWN);
        applications.save(first);
        stubScore(5);

        apply();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(applications.findAll())
                .filteredOn(a -> a.getStatus() == ApplicationStatus.RECEIVED)
                .singleElement().satisfies(a -> assertThat(a.getMatchScore()).isNotNull()));
        assertThat(applications.findAll().stream().filter(a -> a.getStatus() == ApplicationStatus.RECEIVED).findFirst()
                .orElseThrow().getMatchScore().getScore()).isEqualTo(60);
        assertThat(FakeServices.calls(AI_SCORE)).hasSize(1);
    }

    @Test
    void noScoreIsRequestedWithoutAnalysedCv() throws Exception {
        stubReadiness(candidateId, true, true, null);
        stubScore(90);

        apply();

        Thread.sleep(300);
        assertThat(FakeServices.calls(AI_SCORE)).isEmpty();
        assertThat(onlyApplication().getMatchScore()).isNull();
    }
}
