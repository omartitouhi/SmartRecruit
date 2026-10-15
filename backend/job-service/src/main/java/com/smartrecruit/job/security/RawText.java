package com.smartrecruit.job.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a JSON string field out of HTML sanitization (BE-SEC-03). Only for values that are never
 * displayed and must be kept byte for byte: passwords, tokens.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface RawText {
}
