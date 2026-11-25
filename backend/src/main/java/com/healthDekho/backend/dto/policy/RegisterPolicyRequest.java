package com.healthDekho.backend.dto.policy;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Everything else on the registration form (customer details, product terms, maturity, totals)
 * is derived on the server, so the client cannot tamper with prices or dates.
 *
 * @param startDate             optional; defaults to today
 * @param recommendationItemId  optional; set when registering from an AI recommendation
 */
public record RegisterPolicyRequest(
        @NotNull(message = "Select an insurance product") @Positive Long productId,
        LocalDate startDate,
        @Positive Long recommendationItemId) {
}
