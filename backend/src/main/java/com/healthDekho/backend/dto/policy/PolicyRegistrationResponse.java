package com.healthDekho.backend.dto.policy;

import com.healthDekho.backend.entity.enums.PolicyStatus;
import com.healthDekho.backend.entity.enums.PremiumFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Confirmation shown after a successful FR-3 registration. */
public record PolicyRegistrationResponse(
        String policyNumber,
        String policyName,
        LocalDate startDate,
        LocalDate maturityDate,
        PremiumFrequency premiumFrequency,
        BigDecimal premiumAmount,
        BigDecimal sumInsured,
        BigDecimal totalPremium,
        int numberOfInstallments,
        LocalDate firstDueDate,
        PolicyStatus status,
        String message) {
}
