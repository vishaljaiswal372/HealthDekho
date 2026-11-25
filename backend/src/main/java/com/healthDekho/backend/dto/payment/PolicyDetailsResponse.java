package com.healthDekho.backend.dto.payment;

import com.healthDekho.backend.entity.enums.PolicyStatus;
import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.entity.enums.PremiumFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

/** FR-4 policy search result, with totals recalculated as of today. */
public record PolicyDetailsResponse(
        String policyNumber,
        String customerName,
        String policyName,
        PolicyType policyType,
        int termMonths,
        LocalDate startDate,
        LocalDate maturityDate,
        PremiumFrequency premiumFrequency,
        BigDecimal premiumAmount,
        BigDecimal sumInsured,
        BigDecimal totalPremium,
        BigDecimal totalPaid,
        BigDecimal totalPenalty,
        BigDecimal outstandingAmount,
        int installmentsPaid,
        int installmentsTotal,
        PolicyStatus status) {
}
