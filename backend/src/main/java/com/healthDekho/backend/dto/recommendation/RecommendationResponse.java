package com.healthDekho.backend.dto.recommendation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.entity.enums.PremiumFrequency;
import com.healthDekho.backend.entity.enums.Suitability;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * FR-2 result. Product figures come from the database, never from the AI; only the
 * ranking, suitability and reason are AI-generated. {@code recommendationItemId} is passed
 * to registration so the item can be marked as selected.
 */
public record RecommendationResponse(
        Long requestId,
        int calculatedAge,
        OffsetDateTime requestedAt,
        List<Item> recommendations,
        @JsonInclude(JsonInclude.Include.NON_NULL) String message) {

    public record Item(
            Long recommendationItemId,
            int rank,
            Long productId,
            String productCode,
            String policyName,
            PolicyType policyType,
            int termMonths,
            BigDecimal sumInsured,
            PremiumFrequency premiumFrequency,
            BigDecimal premiumAmount,
            String recommendationReason,
            Suitability suitability,
            boolean selectedForRegistration) {
    }
}
