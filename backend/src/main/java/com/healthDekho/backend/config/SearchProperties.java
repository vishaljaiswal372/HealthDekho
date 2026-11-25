package com.healthDekho.backend.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** FR-5: requested page sizes are capped at {@code maxPageSize} (bounded result retrieval). */
@Validated
@ConfigurationProperties(prefix = "healthdekho.search")
public record SearchProperties(
        @Min(1) @Max(500) int maxPageSize,
        @Min(1) int defaultPageSize) {

    public SearchProperties {
        if (defaultPageSize <= 0) defaultPageSize = 10;
    }
}
