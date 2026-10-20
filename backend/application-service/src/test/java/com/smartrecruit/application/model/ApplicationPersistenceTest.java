package com.smartrecruit.application.model;

import com.smartrecruit.application.dto.MatchCriterionDto;
import com.smartrecruit.application.dto.MatchScoreDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The match score is persisted as a JSON column and read back unchanged (BE-APP-01). */
@SpringBootTest
class ApplicationPersistenceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transaction;

    private Application save(MatchScoreDto score) {
        Application application = Application.builder()
                .id(UUID.randomUUID()).jobId(UUID.randomUUID()).candidateId(UUID.randomUUID())
                .status(ApplicationStatus.RECEIVED).matchScore(score).appliedAt(Instant.now()).build();
        transaction.executeWithoutResult(status -> entityManager.persist(application));
        return application;
    }

    private Application reload(UUID id) {
        return transaction.execute(status -> {
            entityManager.clear();
            return entityManager.find(Application.class, id);
        });
    }

    @Test
    void matchScoreRoundTripsAsJson() {
        MatchScoreDto score = new MatchScoreDto(82.5, List.of(new MatchCriterionDto("Skills", 0.6, 49.5, "5 of 6 skills")));

        MatchScoreDto loaded = reload(save(score).getId()).getMatchScore();

        assertThat(loaded.getScore()).isEqualTo(82.5);
        assertThat(loaded.getCriteria()).singleElement().satisfies(criterion -> {
            assertThat(criterion.getLabel()).isEqualTo("Skills");
            assertThat(criterion.getWeight()).isEqualTo(0.6);
            assertThat(criterion.getContribution()).isEqualTo(49.5);
            assertThat(criterion.getExplanation()).isEqualTo("5 of 6 skills");
        });
    }

    @Test
    void missingMatchScoreStaysNull() {
        assertThat(reload(save(null).getId()).getMatchScore()).isNull();
    }
}
