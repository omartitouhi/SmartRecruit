package com.smartrecruit.application.service;

import com.smartrecruit.application.exception.ApiException;
import com.smartrecruit.application.support.ApiTestSupport;
import com.smartrecruit.application.support.FakeServices;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BE-MATCH-01 / BE-MATCH-02 — scoring and cache validity. */
class MatchScoreServiceTest extends ApiTestSupport {

    @Autowired
    private MatchScoreService service;

    private final UUID candidateId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final Instant parsedAt = Instant.parse("2026-01-02T10:00:00.123456789Z");
    private final Instant jobUpdatedAt = Instant.parse("2026-01-01T10:00:00.987654321Z");

    @Test
    void missCallsAiAndStoresTheScore() {
        stubScore(80);

        var score = service.scoreFor(candidateId, jobId, parsedAt, jobUpdatedAt);

        assertThat(score.getScore()).isEqualTo(80);
        assertThat(score.getCriteria()).singleElement().satisfies(c -> assertThat(c.getLabel()).isEqualTo("Skills"));
        assertThat(FakeServices.calls(AI_SCORE)).singleElement().satisfies(call -> {
            assertThat(call.body()).contains(candidateId.toString()).contains(jobId.toString());
            assertThat(call.role()).isEqualTo("SERVICE");
            assertThat(call.h2cUpgrade()).isFalse();
        });
        assertThat(scoreCache.count()).isEqualTo(1);
    }

    @Test
    void unchangedPairIsServedFromCacheEvenWithNanosecondInstants() {
        stubScore(80);
        service.scoreFor(candidateId, jobId, parsedAt, jobUpdatedAt);
        stubScore(10);

        var score = service.scoreFor(candidateId, jobId, parsedAt, jobUpdatedAt);

        assertThat(score.getScore()).isEqualTo(80);
        assertThat(FakeServices.calls(AI_SCORE)).hasSize(1);
    }

    @Test
    void newCvAnalysisInvalidatesTheEntry() {
        stubScore(80);
        service.scoreFor(candidateId, jobId, parsedAt, jobUpdatedAt);
        stubScore(55);

        var score = service.scoreFor(candidateId, jobId, parsedAt.plusSeconds(60), jobUpdatedAt);

        assertThat(score.getScore()).isEqualTo(55);
        assertThat(FakeServices.calls(AI_SCORE)).hasSize(2);
        assertThat(scoreCache.count()).isEqualTo(1);
    }

    @Test
    void editedJobInvalidatesTheEntry() {
        stubScore(80);
        service.scoreFor(candidateId, jobId, parsedAt, jobUpdatedAt);
        stubScore(35);

        var score = service.scoreFor(candidateId, jobId, parsedAt, jobUpdatedAt.plusSeconds(60));

        assertThat(score.getScore()).isEqualTo(35);
        assertThat(service.scoreFor(candidateId, jobId, parsedAt, jobUpdatedAt.plusSeconds(60)).getScore()).isEqualTo(35);
        assertThat(FakeServices.calls(AI_SCORE)).hasSize(2);
    }

    @Test
    void otherPairsDoNotShareEntries() {
        stubScore(80);
        service.scoreFor(candidateId, jobId, parsedAt, jobUpdatedAt);

        service.scoreFor(UUID.randomUUID(), jobId, parsedAt, jobUpdatedAt);
        service.scoreFor(candidateId, UUID.randomUUID(), parsedAt, jobUpdatedAt);

        assertThat(FakeServices.calls(AI_SCORE)).hasSize(3);
        assertThat(scoreCache.count()).isEqualTo(3);
    }

    @Test
    void aiFailureIs503AndNothingIsCached() {
        FakeServices.on(AI_SCORE, 500, "");

        assertThatThrownBy(() -> service.scoreFor(candidateId, jobId, parsedAt, jobUpdatedAt))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(scoreCache.count()).isZero();
    }

    @Test
    void emptyAiAnswerIs503() {
        FakeServices.on(AI_SCORE, 200, "");

        assertThatThrownBy(() -> service.scoreFor(candidateId, jobId, parsedAt, jobUpdatedAt))
                .isInstanceOf(ApiException.class);
    }
}
