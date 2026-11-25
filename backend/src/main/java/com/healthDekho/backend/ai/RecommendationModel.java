package com.healthDekho.backend.ai;

import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.entity.enums.PremiumFrequency;

import java.math.BigDecimal;
import java.util.List;

/**
 * Boundary to the AI model. The model only ranks and explains; the service decides which
 * products are candidates and validates everything that comes back.
 */
public interface RecommendationModel {

    /**
     * @throws AiUnavailableException if the model cannot be reached or returns unusable output
     */
    ModelResult recommend(Input input);

    /** What the model is told. Deliberately contains no name, email or other identifying data. */
    record Input(int age, String insuranceGoals, Integer preferredTermMonths, PolicyType preferredPolicyType,
                 int maxRecommendations, List<Candidate> candidates) {
    }

    record Candidate(String productCode, String policyName, PolicyType policyType, int termMonths,
                     PremiumFrequency premiumFrequency, BigDecimal premiumAmount, BigDecimal sumInsured) {
    }

    /** Raw model output, before validation. Fields may be missing or wrong. */
    record ModelResult(String modelName, List<Pick> picks) {
    }

    record Pick(String productCode, Integer rank, String suitability, String reason) {
    }
}
