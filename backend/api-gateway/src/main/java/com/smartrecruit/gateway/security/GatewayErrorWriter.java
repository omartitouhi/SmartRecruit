package com.smartrecruit.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Errors produced by the gateway itself, in the shared error format ({@code DATA_MODEL.md} §1).
 */
public final class GatewayErrorWriter {

    private GatewayErrorWriter() {
    }

    public static void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
                             String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}"
                .formatted(Instant.now(), status.value(), status.getReasonPhrase(), escape(message),
                        escape(request.getRequestURI())));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
