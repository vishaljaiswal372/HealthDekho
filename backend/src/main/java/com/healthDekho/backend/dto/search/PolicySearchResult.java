package com.healthDekho.backend.dto.search;

import com.healthDekho.backend.entity.enums.PolicyStatus;
import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.entity.enums.PremiumFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of the FR-5 results table. {@code remainingPremium} is the outstanding amount as of today. */
public record PolicySearchResult(
        String policyNumber,
        String customerName,
        LocalDate dateOfBirth,
        String policyName,
        PolicyType policyType,
        LocalDate startDate,
        LocalDate maturityDate,
        PremiumFrequency premiumFrequency,
        BigDecimal premiumAmount,
        BigDecimal sumInsured,
        BigDecimal totalPremium,
        PolicyStatus status,
        BigDecimal remainingPremium) {
}
