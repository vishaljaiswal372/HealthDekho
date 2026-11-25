package com.healthDekho.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Stable paging envelope (Spring's Page JSON is not a stable API). {@code message} is set when nothing matched. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        @JsonInclude(JsonInclude.Include.NON_NULL) String message) {
}
