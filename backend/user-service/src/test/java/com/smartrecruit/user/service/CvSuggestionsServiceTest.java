package com.smartrecruit.user.service;

import com.smartrecruit.user.ai.CvAnalysisClient;
import com.smartrecruit.user.ai.ParsedCv;
import com.smartrecruit.user.dto.CvSuggestionsResponse;
import com.smartrecruit.user.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** BE-CV-02 — conversion of the AI result into profile suggestions. */
class CvSuggestionsServiceTest {

    private final CvAnalysisClient client = mock(CvAnalysisClient.class);
    private final CvSuggestionsService service = new CvSuggestionsService(client);
    private final UUID candidateId = UUID.randomUUID();

    @Test
    void fullDatesBecomeYearMonthAndEverythingElseIsKept() {
        when(client.findParsedCv(candidateId)).thenReturn(Optional.of(new ParsedCv(
                List.of("Java", "Spring"),
                List.of(new ParsedCv.Experience("Dev", "Acme", "2022-01-15", null, "APIs"),
                        new ParsedCv.Experience("Intern", "Globex", "2020-06-01", "2021-12-31", "Tests")),
                List.of(new ParsedCv.Education("Master", "ESPRIT", 2025)),
                List.of("Français"))));

        CvSuggestionsResponse result = service.suggestionsFor(candidateId);

        assertThat(result.skills()).containsExactly("Java", "Spring");
        assertThat(result.languages()).containsExactly("Français");
        assertThat(result.experiences()).hasSize(2);
        assertThat(result.experiences().get(0).getStartDate()).isEqualTo(YearMonth.of(2022, 1));
        assertThat(result.experiences().get(0).getEndDate()).isNull();
        assertThat(result.experiences().get(0).getTitle()).isEqualTo("Dev");
        assertThat(result.experiences().get(0).getCompany()).isEqualTo("Acme");
        assertThat(result.experiences().get(0).getDescription()).isEqualTo("APIs");
        assertThat(result.experiences().get(1).getStartDate()).isEqualTo(YearMonth.of(2020, 6));
        assertThat(result.experiences().get(1).getEndDate()).isEqualTo(YearMonth.of(2021, 12));
        assertThat(result.education()).singleElement().satisfies(education -> {
            assertThat(education.getDegree()).isEqualTo("Master");
            assertThat(education.getInstitution()).isEqualTo("ESPRIT");
            assertThat(education.getYear()).isEqualTo(2025);
        });
    }

    @Test
    void dateParsingIsTolerant() {
        assertThat(CvSuggestionsService.toYearMonth("2022-01-31")).isEqualTo(YearMonth.of(2022, 1));
        assertThat(CvSuggestionsService.toYearMonth("2022-01")).isEqualTo(YearMonth.of(2022, 1));
        assertThat(CvSuggestionsService.toYearMonth(" 2022-03-01 ")).isEqualTo(YearMonth.of(2022, 3));
        assertThat(CvSuggestionsService.toYearMonth(null)).isNull();
        assertThat(CvSuggestionsService.toYearMonth("  ")).isNull();
        assertThat(CvSuggestionsService.toYearMonth("janvier 2022")).isNull();
        assertThat(CvSuggestionsService.toYearMonth("2022-13-45")).isNull();
    }

    @Test
    void missingListsBecomeEmpty() {
        when(client.findParsedCv(candidateId)).thenReturn(Optional.of(new ParsedCv(null, null, null, null)));

        CvSuggestionsResponse result = service.suggestionsFor(candidateId);

        assertThat(result.skills()).isEmpty();
        assertThat(result.experiences()).isEmpty();
        assertThat(result.education()).isEmpty();
        assertThat(result.languages()).isEmpty();
    }

    @Test
    void noAnalysedCvIs404() {
        when(client.findParsedCv(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suggestionsFor(candidateId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void anUnreachableAiIs503() {
        when(client.findParsedCv(candidateId)).thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> service.suggestionsFor(candidateId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
