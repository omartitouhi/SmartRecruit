package com.smartrecruit.application.config;

import com.smartrecruit.application.security.SanitizingStringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * XSS protection on input (BE-SEC-03): all JSON request strings are sanitized, so every endpoint —
 * existing or future — stores plain text only.
 */
@Configuration
public class JacksonSanitizationConfig {

    @Bean
    public JacksonModule inputSanitizationModule() {
        return new SimpleModule("input-sanitization")
                .addDeserializer(String.class, SanitizingStringDeserializer.INSTANCE);
    }
}
