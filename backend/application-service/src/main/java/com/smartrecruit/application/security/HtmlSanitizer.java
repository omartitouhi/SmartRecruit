package com.smartrecruit.application.security;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

/**
 * Turns user input into plain text (BE-SEC-03): HTML tags, scripts, event handlers and comments are
 * removed, the text content is kept as is (line breaks included). The result is stored as plain
 * text, not as HTML entities, so the frontend escapes it once on display.
 */
public final class HtmlSanitizer {

    private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);
    private static final int MAX_PASSES = 5;

    private HtmlSanitizer() {
    }

    public static String toPlainText(String input) {
        if (input == null || (input.indexOf('<') < 0 && input.indexOf('&') < 0)) {
            return input;
        }
        String current = input;
        // Repeated because decoding entities can reveal new markup ("&lt;script&gt;" → "<script>").
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            String cleaned = Parser.unescapeEntities(Jsoup.clean(current, "", Safelist.none(), OUTPUT), false);
            if (cleaned.equals(current)) {
                return cleaned;
            }
            current = cleaned;
        }
        return current.replace("<", "").replace(">", "");
    }
}
