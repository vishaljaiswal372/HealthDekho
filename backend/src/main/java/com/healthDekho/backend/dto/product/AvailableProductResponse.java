package com.healthDekho.backend.dto.product;

import com.healthDekho.backend.entity.InsuranceProduct;
import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.entity.enums.PremiumFrequency;

import java.math.BigDecimal;

/** One row of the FR-1 "available insurance products" table; productId drives the Register action. */
public record AvailableProductResponse(
        Long productId,
        String productCode,
        String policyName,
        PolicyType policyType,
        int termMonths,
        BigDecimal sumInsured,
        PremiumFrequency premiumFrequency,
        BigDecimal premiumAmount,
        int minEntryAge,
        int maxEntryAge) {

    public static AvailableProductResponse from(InsuranceProduct p) {
        return new AvailableProductResponse(
                p.getProductId(),
                p.getProductCode(),
                p.getPolicyName(),
                p.getPolicyType(),
                p.getTermMonths(),
                p.getSumInsured(),
                p.getPremiumFrequency(),
                p.getPremiumAmount(),
                p.getMinEntryAge(),
                p.getMaxEntryAge());
    }
}
