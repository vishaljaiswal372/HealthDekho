package com.healthDekho.backend.entity.enums;

/**
 * How often a premium is paid. {@code monthsPerInstallment} drives schedule generation
 * and total-premium calculation (installments = termMonths / monthsPerInstallment).
 */
public enum PremiumFrequency {
    MONTHLY(1),
    QUARTERLY(3),
    HALF_YEARLY(6),
    YEARLY(12);

    private final int monthsPerInstallment;

    PremiumFrequency(int monthsPerInstallment) {
        this.monthsPerInstallment = monthsPerInstallment;
    }

    public int monthsPerInstallment() {
        return monthsPerInstallment;
    }

    public int installmentsFor(int termMonths) {
        if (termMonths <= 0 || termMonths % monthsPerInstallment != 0) {
            throw new IllegalArgumentException(
                    "Term of %d months is not a whole number of %s periods".formatted(termMonths, this));
        }
        return termMonths / monthsPerInstallment;
    }
}
