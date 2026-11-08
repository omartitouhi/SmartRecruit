package com.smartrecruit.job.service;

import com.smartrecruit.job.dto.JobPatchRequest;
import com.smartrecruit.job.exception.ApiException;
import com.smartrecruit.job.exception.RequestValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Turns a raw PATCH body into a validated {@link JobPatchRequest}. The raw JSON is needed to know
 * which keys were sent, since Jackson maps "absent" and "null" to the same value.
 */
@Component
@RequiredArgsConstructor
public class JobPatchParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public JobPatchRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le corps de la requête doit être un objet JSON");
        }
        Set<String> present = body.propertyNames().stream()
                .filter(JobPatchRequest.FIELDS::contains)
                .collect(Collectors.toSet());

        JobPatchRequest request;
        try {
            request = objectMapper.treeToValue(body, JobPatchRequest.class).withPresentFields(present);
        } catch (JacksonException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Corps de requête illisible ou mal formé");
        }

        Map<String, String> errors = new TreeMap<>();
        for (String field : present) {
            if (body.get(field).isNull() && !JobPatchRequest.NULLABLE_FIELDS.contains(field)) {
                errors.put(field, "Ce champ est obligatoire et ne peut pas être supprimé");
            }
        }
        for (ConstraintViolation<JobPatchRequest> violation : validator.validate(request)) {
            errors.putIfAbsent(fieldPath(violation), violation.getMessage());
        }
        if (!errors.isEmpty()) {
            throw new RequestValidationException(new LinkedHashMap<>(errors));
        }
        return request;
    }

    /** {@code requiredSkills[0].<list element>} → {@code requiredSkills[0]}, same names as {@code @Valid} binding errors. */
    private static String fieldPath(ConstraintViolation<?> violation) {
        return violation.getPropertyPath().toString().replace(".<list element>", "");
    }
}
