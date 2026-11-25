package com.healthDekho.backend.dto.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** FR-5 query parameters. Which combination is allowed is decided by {@link SearchCriteria#from}. */
public record PolicySearchRequest(
        @Size(max = 30, message = "Policy identifier is too long")
        String policyNumber,

        @Size(min = 2, max = 100, message = "Enter at least 2 characters of the customer name")
        String customerName,

        @PastOrPresent(message = "Date of birth cannot be in the future")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateOfBirth,

        @Min(value = 0, message = "Page must be 0 or more")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must be at most 100")
        Integer size) {
}
