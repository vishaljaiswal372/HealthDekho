package com.healthDekho.backend.dto.policy;

import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.entity.enums.PremiumFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pre-populated FR-3 registration form. All values are computed by the server.
 * When {@code eligible} is false, {@code ineligibilityReason} explains why and registration will be refused.
 */
public record RegistrationPreviewResponse(
        Long productId,
        String productCode,
        String customerName,
        LocalDate dateOfBirth,
        String email,
        String phone,
        int ageAtStart,
        PolicyType policyType,
        String policyName,
        int termMonths,
        LocalDate startDate,
        LocalDate maturityDate,
        PremiumFrequency premiumFrequency,
        BigDecimal premiumAmount,
        BigDecimal sumInsured,
        int numberOfInstallments,
        BigDecimal totalPremiumPayable,
        boolean eligible,
        String ineligibilityReason) {
}
