package com.healthDekho.backend.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/** FR-4 rules: grace period after each due date, and the penalty rate applied after it. */
@Validated
@ConfigurationProperties(prefix = "healthdekho.payment")
public record PaymentProperties(
        @Min(0) int gracePeriodMonths,
        @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal latePenaltyRate) {
}
