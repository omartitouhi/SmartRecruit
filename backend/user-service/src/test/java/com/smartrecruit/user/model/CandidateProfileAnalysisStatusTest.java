package com.smartrecruit.user.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-CV-02 — stored status wins; rows saved before the field existed get it derived. */
class CandidateProfileAnalysisStatusTest {

    private CandidateProfile.CandidateProfileBuilder profile() {
        return CandidateProfile.builder().firstName("A").lastName("B").email("a@b.test");
    }

    @Test
    void aProfileWithoutCvIsNone() {
        assertThat(profile().build().analysisStatus()).isEqualTo(CvAnalysisStatus.NONE);
    }

    @Test
    void theStoredStatusIsReturnedAsIs() {
        for (CvAnalysisStatus status : CvAnalysisStatus.values()) {
            assertThat(profile().cvStorageKey("k").cvAnalysisStatus(status).build().analysisStatus()).isEqualTo(status);
        }
    }

    @Test
    void legacyRowsAreDerivedFromTheCvFields() {
        assertThat(profile().cvStorageKey("k").cvParsedAt(Instant.now()).build().analysisStatus())
                .isEqualTo(CvAnalysisStatus.DONE);
        assertThat(profile().cvStorageKey("k").build().analysisStatus()).isEqualTo(CvAnalysisStatus.FAILED);
    }
}
