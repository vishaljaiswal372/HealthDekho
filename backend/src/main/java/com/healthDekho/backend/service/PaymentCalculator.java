package com.healthDekho.backend.service;

import com.healthDekho.backend.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Deterministic FR-4 premium rules. Pure functions with no Spring or database.
 *
 * <ul>
 *   <li>An installment is <b>late</b> when paid after its grace end date (due date + 1 month).</li>
 *   <li>Late penalty = rate (10%) x the premium still unpaid at that point. It is applied once per
 *       installment and then stays fixed, so repeated late part-payments are not penalized again.</li>
 *   <li>Amount due = scheduled premium + penalty. Outstanding = amount due - paid.</li>
 * </ul>
 */
public final class PaymentCalculator {

    private PaymentCalculator() {
    }

    public static boolean isLate(LocalDate graceEndDate, LocalDate onDate) {
        return onDate.isAfter(graceEndDate);
    }

    /**
     * Total penalty for the installment as of {@code onDate}: the penalty already applied if any,
     * otherwise {@code rate x unpaid premium} when late, otherwise zero.
     */
    public static BigDecimal penaltyAsOf(BigDecimal scheduledAmount, BigDecimal paidAmount, BigDecimal appliedPenalty,
                                         LocalDate graceEndDate, LocalDate onDate, BigDecimal rate) {
        if (appliedPenalty.signum() > 0 || !isLate(graceEndDate, onDate)) {
            return appliedPenalty;
        }
        BigDecimal unpaidPremium = scheduledAmount.subtract(paidAmount).max(BigDecimal.ZERO);
        return unpaidPremium.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal outstanding(BigDecimal scheduledAmount, BigDecimal penalty, BigDecimal paidAmount) {
        return scheduledAmount.add(penalty).subtract(paidAmount).max(BigDecimal.ZERO);
    }

    /** Status as of {@code onDate}. OVERDUE wins over PARTIALLY_PAID once the grace period has passed. */
    public static PaymentStatus statusAsOf(BigDecimal amountDue, BigDecimal paidAmount, LocalDate graceEndDate,
                                           LocalDate onDate) {
        Settlement settlement = paidAmount.compareTo(amountDue) >= 0 ? Settlement.FULL
                : paidAmount.signum() > 0 ? Settlement.PARTIAL
                : Settlement.NONE;
        boolean late = isLate(graceEndDate, onDate);
        return switch (settlement) {
            case FULL -> PaymentStatus.PAID;
            case PARTIAL -> late ? PaymentStatus.OVERDUE : PaymentStatus.PARTIALLY_PAID;
            case NONE -> late ? PaymentStatus.OVERDUE : PaymentStatus.PENDING;
        };
    }

    /** Whether the customer may select this installment for payment. */
    public static boolean isPayable(PaymentStatus status) {
        return switch (status) {
            case PAID -> false;
            case PENDING, PARTIALLY_PAID, OVERDUE -> true;
        };
    }

    private enum Settlement { FULL, PARTIAL, NONE }
}
