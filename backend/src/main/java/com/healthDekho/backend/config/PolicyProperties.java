package com.healthDekho.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Policy numbers look like {@code <numberPrefix>-<year>-<0001>}, e.g. HDP-2026-0003. */
@Validated
@ConfigurationProperties(prefix = "healthdekho.policy")
public record PolicyProperties(
        @NotBlank @Pattern(regexp = "[A-Z]{2,10}") String numberPrefix) {
}
