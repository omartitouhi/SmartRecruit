package com.smartrecruit.user.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<svg/onload=alert(1)>",
            "<iframe src=\"javascript:alert(1)\"></iframe>",
            "&lt;script&gt;alert(1)&lt;/script&gt;",
            "&amp;lt;script&amp;gt;alert(1)&amp;lt;/script&amp;gt;",
            "<<script>script>alert(1)<</script>/script>",
            "<!-- <script>alert(1)</script> -->",
            "<a href=\"javascript:alert(1)\">clic</a>"
    })
    void noMarkupSurvives(String attack) {
        String result = HtmlSanitizer.toPlainText(attack);

        assertThat(result).doesNotContain("<script").doesNotContain("<img").doesNotContain("<svg")
                .doesNotContain("<iframe").doesNotContain("<a ").doesNotContain("onerror");
        assertThat(HtmlSanitizer.toPlainText(result)).isEqualTo(result);
    }

    @Test
    void textContentIsKept() {
        assertThat(HtmlSanitizer.toPlainText("<p>Bonjour <b>Alice</b></p>")).isEqualTo("Bonjour Alice");
        assertThat(HtmlSanitizer.toPlainText("Tom & Jerry")).isEqualTo("Tom & Jerry");
        assertThat(HtmlSanitizer.toPlainText("5 < 10")).isEqualTo("5 < 10");
        assertThat(HtmlSanitizer.toPlainText("ligne 1\nligne 2")).isEqualTo("ligne 1\nligne 2");
        assertThat(HtmlSanitizer.toPlainText("")).isEmpty();
        assertThat(HtmlSanitizer.toPlainText(null)).isNull();
    }
}
