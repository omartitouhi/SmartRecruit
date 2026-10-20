package com.smartrecruit.user.service;

import com.smartrecruit.user.dto.CompanyPatchRequest;
import com.smartrecruit.user.exception.ApiException;
import com.smartrecruit.user.exception.RequestValidationException;
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
 * Turns a raw company PATCH body into a validated {@link CompanyPatchRequest}; the raw JSON is needed
 * to know which keys were sent (same approach as {@link CandidateProfilePatchParser}).
 */
@Component
@RequiredArgsConstructor
public class CompanyPatchParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CompanyPatchRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le corps de la requête doit être un objet JSON");
        }
        Set<String> present = body.propertyNames().stream().collect(Collectors.toSet());

        CompanyPatchRequest request;
        try {
            request = objectMapper.treeToValue(body, CompanyPatchRequest.class).withPresentFields(present);
        } catch (JacksonException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Corps de requête illisible ou mal formé");
        }

        Map<String, String> errors = new TreeMap<>();
        for (String field : CompanyPatchRequest.MANDATORY_FIELDS) {
            if (present.contains(field) && body.get(field).isNull()) {
                errors.put(field, "Ce champ est obligatoire et ne peut pas être supprimé");
            }
        }
        for (ConstraintViolation<CompanyPatchRequest> violation : validator.validate(request)) {
            errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }
        if (!errors.isEmpty()) {
            throw new RequestValidationException(new LinkedHashMap<>(errors));
        }
        return request;
    }
}
