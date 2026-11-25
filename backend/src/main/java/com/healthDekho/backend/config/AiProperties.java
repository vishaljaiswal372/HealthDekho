package com.healthDekho.backend.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** FR-2 settings. Versions are stored with every recommendation request for auditability. */
@Validated
@ConfigurationProperties(prefix = "healthdekho.ai")
public record AiProperties(
        @NotBlank String promptVersion,
        @NotBlank String modelVersion,
        @Min(1) @Max(10) int maxRecommendations) {
}
