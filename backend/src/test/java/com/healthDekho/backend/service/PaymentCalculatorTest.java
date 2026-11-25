package com.healthDekho.backend.service;

import com.healthDekho.backend.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCalculatorTest {

    private static final BigDecimal RATE = new BigDecimal("0.10");
    private static final LocalDate GRACE_END = LocalDate.of(2027, 1, 20);

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void lateMeansStrictlyAfterGraceEnd() {
        assertThat(PaymentCalculator.isLate(GRACE_END, GRACE_END)).isFalse();
        assertThat(PaymentCalculator.isLate(GRACE_END, GRACE_END.plusDays(1))).isTrue();
    }

    @Test
    void noPenaltyWithinGracePeriod() {
        assertThat(PaymentCalculator.penaltyAsOf(bd("1250.00"), bd("0"), bd("0"), GRACE_END, GRACE_END, RATE))
                .isEqualByComparingTo("0");
    }

    @Test
    void penaltyIsTenPercentOfUnpaidPremium() {
        // seed installment 4002: 1250 scheduled, 500 paid -> 750 unpaid -> 75 penalty
        assertThat(PaymentCalculator.penaltyAsOf(bd("1250.00"), bd("500.00"), bd("0"), GRACE_END,
                GRACE_END.plusDays(1), RATE)).isEqualByComparingTo("75.00");
        assertThat(PaymentCalculator.penaltyAsOf(bd("333.33"), bd("0"), bd("0"), GRACE_END,
                GRACE_END.plusDays(1), RATE)).isEqualByComparingTo("33.33");
    }

    @Test
    void appliedPenaltyIsNeverRecalculated() {
        assertThat(PaymentCalculator.penaltyAsOf(bd("1250.00"), bd("1000.00"), bd("75.00"), GRACE_END,
                GRACE_END.plusMonths(6), RATE)).isEqualByComparingTo("75.00");
    }

    @Test
    void statusFollowsPaymentAndGracePeriod() {
        LocalDate inGrace = GRACE_END.minusDays(1);
        LocalDate late = GRACE_END.plusDays(1);
        assertThat(PaymentCalculator.statusAsOf(bd("1250"), bd("0"), GRACE_END, inGrace)).isEqualTo(PaymentStatus.PENDING);
        assertThat(PaymentCalculator.statusAsOf(bd("1250"), bd("500"), GRACE_END, inGrace)).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(PaymentCalculator.statusAsOf(bd("1250"), bd("1250"), GRACE_END, inGrace)).isEqualTo(PaymentStatus.PAID);
        assertThat(PaymentCalculator.statusAsOf(bd("1250"), bd("0"), GRACE_END, late)).isEqualTo(PaymentStatus.OVERDUE);
        assertThat(PaymentCalculator.statusAsOf(bd("1375"), bd("1250"), GRACE_END, late)).isEqualTo(PaymentStatus.OVERDUE);
        assertThat(PaymentCalculator.statusAsOf(bd("1375"), bd("1375"), GRACE_END, late)).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void outstandingIncludesPenaltyAndNeverGoesNegative() {
        assertThat(PaymentCalculator.outstanding(bd("1250"), bd("75"), bd("500"))).isEqualByComparingTo("825");
        assertThat(PaymentCalculator.outstanding(bd("1250"), bd("0"), bd("1300"))).isEqualByComparingTo("0");
    }

    @Test
    void onlyUnpaidInstallmentsArePayable() {
        assertThat(PaymentCalculator.isPayable(PaymentStatus.PAID)).isFalse();
        assertThat(PaymentCalculator.isPayable(PaymentStatus.PENDING)).isTrue();
        assertThat(PaymentCalculator.isPayable(PaymentStatus.PARTIALLY_PAID)).isTrue();
        assertThat(PaymentCalculator.isPayable(PaymentStatus.OVERDUE)).isTrue();
    }
}
