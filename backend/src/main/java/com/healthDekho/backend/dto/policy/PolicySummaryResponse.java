package com.healthDekho.backend.dto.policy;

import com.healthDekho.backend.entity.CustomerPolicy;
import com.healthDekho.backend.entity.enums.PolicyStatus;
import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.entity.enums.PremiumFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of the FR-1 customer dashboard table. */
public record PolicySummaryResponse(
        String policyNumber,
        String policyName,
        PolicyType policyType,
        int termMonths,
        LocalDate startDate,
        LocalDate maturityDate,
        PremiumFrequency premiumFrequency,
        BigDecimal premiumAmount,
        BigDecimal sumInsured,
        PolicyStatus status) {

    public static PolicySummaryResponse from(CustomerPolicy p) {
        return new PolicySummaryResponse(
                p.getPolicyNumber(),
                p.getProduct().getPolicyName(),
                p.getProduct().getPolicyType(),
                p.getTermMonths(),
                p.getStartDate(),
                p.getMaturityDate(),
                p.getPremiumFrequency(),
                p.getPremiumAmount(),
                p.getSumInsured(),
                p.getStatus());
    }
}
