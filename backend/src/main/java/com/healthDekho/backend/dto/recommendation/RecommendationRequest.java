package com.healthDekho.backend.dto.recommendation;

import com.healthDekho.backend.entity.enums.PolicyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * FR-2 inputs; at least one is required. When {@code dateOfBirth} is omitted the profile date of birth is used.
 */
public record RecommendationRequest(
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Size(max = 500, message = "Keep insurance goals under 500 characters")
        String insuranceGoals,

        @Min(value = 1, message = "Preferred term must be at least 1 month")
        @Max(value = 600, message = "Preferred term must be at most 600 months")
        Integer preferredTermMonths,

        PolicyType preferredPolicyType) {

    public boolean hasAnyInput() {
        return dateOfBirth != null || (insuranceGoals != null && !insuranceGoals.isBlank())
                || preferredTermMonths != null || preferredPolicyType != null;
    }
}
