package com.healthDekho.backend.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings. Startup fails if the secret is missing or too short for HS256.
 */
@Validated
@ConfigurationProperties(prefix = "healthdekho.security")
public record SecurityProperties(
        @NotNull @Size(min = 32, message = "must be at least 32 characters (set JWT_SECRET)") String jwtSecret,
        @NotNull Duration tokenValidity) {
}
