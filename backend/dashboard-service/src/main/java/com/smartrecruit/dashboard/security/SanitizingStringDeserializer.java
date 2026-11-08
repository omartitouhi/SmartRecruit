package com.smartrecruit.dashboard.security;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.jdk.StringDeserializer;

/**
 * Replaces Jackson's default {@code String} deserializer: every string of every JSON request body
 * is turned into plain text by {@link HtmlSanitizer} before reaching a controller (BE-SEC-03),
 * including list elements. Fields annotated {@link RawText} are left untouched.
 */
public class SanitizingStringDeserializer extends ValueDeserializer<String> {

    public static final SanitizingStringDeserializer INSTANCE = new SanitizingStringDeserializer(false);
    static final SanitizingStringDeserializer RAW = new SanitizingStringDeserializer(true);

    private final boolean raw;

    private SanitizingStringDeserializer(boolean raw) {
        this.raw = raw;
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        String value = StringDeserializer.instance.deserialize(parser, context);
        return raw ? value : HtmlSanitizer.toPlainText(value);
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        boolean rawProperty = property != null && property.getAnnotation(RawText.class) != null;
        return rawProperty ? RAW : INSTANCE;
    }

    @Override
    public Object getNullValue(DeserializationContext context) {
        return null;
    }
}
