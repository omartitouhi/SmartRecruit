package com.smartrecruit.user.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Validation failure detected outside of {@code @Valid} binding; rendered as 400 with
 * per-field messages, like bean validation errors.
 */
@Getter
public class RequestValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public RequestValidationException(Map<String, String> fieldErrors) {
        super("La requête contient des champs invalides");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }
}
