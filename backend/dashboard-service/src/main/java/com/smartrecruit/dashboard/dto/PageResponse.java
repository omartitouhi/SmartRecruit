package com.smartrecruit.dashboard.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Pagination envelope shared by every service ({@code DATA_MODEL.md} §1). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
