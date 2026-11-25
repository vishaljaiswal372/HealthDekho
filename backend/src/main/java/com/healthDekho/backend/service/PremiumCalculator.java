package com.healthDekho.backend.service;

import com.healthDekho.backend.entity.enums.PremiumFrequency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Deterministic policy arithmetic (FR-3). Pure functions with no Spring or database,
 * so every rule is unit-testable. AI never feeds into these numbers.
 */
public final class PremiumCalculator {

    private PremiumCalculator() {
    }

    /** One scheduled installment before it is persisted. */
    public record ScheduledInstallment(int installmentNo, LocalDate dueDate, LocalDate graceEndDate, BigDecimal amount) {
    }

    /** Completed years of age on the given date (birthday counts on the day itself). */
    public static int ageOn(LocalDate dateOfBirth, LocalDate onDate) {
        if (dateOfBirth.isAfter(onDate)) {
            throw new IllegalArgumentException("Date of birth is after " + onDate);
        }
        return Period.between(dateOfBirth, onDate).getYears();
    }

    /** Last day of cover: the day before the term's anniversary (e.g. 2026-11-20 + 12 months -> 2027-11-19). */
    public static LocalDate maturityDate(LocalDate startDate, int termMonths) {
        requirePositive(termMonths, "termMonths");
        return startDate.plusMonths(termMonths).minusDays(1);
    }

    public static int numberOfInstallments(PremiumFrequency frequency, int termMonths) {
        return frequency.installmentsFor(termMonths);
    }

    /** Total premium payable = premium per installment x number of installments. */
    public static BigDecimal totalPremium(BigDecimal premiumAmount, PremiumFrequency frequency, int termMonths) {
        requirePositive(premiumAmount, "premiumAmount");
        return premiumAmount
                .multiply(BigDecimal.valueOf(numberOfInstallments(frequency, termMonths)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Installment n is due {@code (n-1) x monthsPerInstallment} months after the start date
     * (always offset from the start, so month-end dates don't drift), with a grace period after each due date.
     */
    public static List<ScheduledInstallment> schedule(LocalDate startDate, PremiumFrequency frequency, int termMonths,
                                                      BigDecimal premiumAmount, int gracePeriodMonths) {
        requirePositive(premiumAmount, "premiumAmount");
        BigDecimal amount = premiumAmount.setScale(2, RoundingMode.HALF_UP);
        int step = frequency.monthsPerInstallment();
        return IntStream.rangeClosed(1, numberOfInstallments(frequency, termMonths))
                .mapToObj(n -> {
                    LocalDate due = startDate.plusMonths((long) (n - 1) * step);
                    return new ScheduledInstallment(n, due, due.plusMonths(gracePeriodMonths), amount);
                })
                .toList();
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
