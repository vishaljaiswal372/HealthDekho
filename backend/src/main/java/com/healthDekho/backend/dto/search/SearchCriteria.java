package com.healthDekho.backend.dto.search;

import com.healthDekho.backend.exception.InvalidRequestException;

import java.time.LocalDate;
import java.util.Locale;

/** The three FR-5 search modes. Sealed, so the service's switch must handle every one. */
public sealed interface SearchCriteria {

    record ByPolicyNumber(String policyNumber) implements SearchCriteria {
    }

    record ByCustomerName(String customerName) implements SearchCriteria {
    }

    record ByPolicyNumberAndDateOfBirth(String policyNumber, LocalDate dateOfBirth) implements SearchCriteria {
    }

    String ALLOWED = "Search by policy identifier, by customer name, or by policy identifier and date of birth.";

    static SearchCriteria from(PolicySearchRequest request) {
        String policyNumber = normalize(request.policyNumber());
        String name = normalize(request.customerName());
        LocalDate dob = request.dateOfBirth();

        if (policyNumber != null && name == null) {
            return dob == null
                    ? new ByPolicyNumber(policyNumber.toUpperCase(Locale.ROOT))
                    : new ByPolicyNumberAndDateOfBirth(policyNumber.toUpperCase(Locale.ROOT), dob);
        }
        if (name != null && policyNumber == null && dob == null) {
            return new ByCustomerName(name);
        }
        if (dob != null && policyNumber == null) {
            throw new InvalidRequestException("Date of birth must be combined with a policy identifier. " + ALLOWED);
        }
        throw new InvalidRequestException(ALLOWED);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
