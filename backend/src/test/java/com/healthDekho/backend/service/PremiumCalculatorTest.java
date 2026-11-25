package com.healthDekho.backend.service;

import com.healthDekho.backend.entity.enums.PremiumFrequency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PremiumCalculatorTest {

    @ParameterizedTest(name = "{1} over {2} months at {0} -> {3}")
    @CsvSource({
            "1250.00,  MONTHLY,     240, 300000.00",  // seed policy HDP-2026-0001
            "18000.00, YEARLY,       12,  18000.00",  // seed policy HDP-2026-0002
            "7500.00,  QUARTERLY,   120, 300000.00",  // HD-SAV-001: 40 installments
            "999.99,   HALF_YEARLY,  60,   9999.90"
    })
    void totalPremium(String premium, PremiumFrequency frequency, int termMonths, String expected) {
        assertThat(PremiumCalculator.totalPremium(new BigDecimal(premium), frequency, termMonths))
                .isEqualByComparingTo(expected)
                .hasScaleOf(2);
    }

    @Test
    void totalPremiumRejectsTermsThatDoNotFitTheFrequency() {
        assertThatThrownBy(() -> PremiumCalculator.totalPremium(BigDecimal.TEN, PremiumFrequency.QUARTERLY, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PremiumCalculator.totalPremium(BigDecimal.ZERO, PremiumFrequency.MONTHLY, 12))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maturityIsTheDayBeforeTheTermAnniversary() {
        assertThat(PremiumCalculator.maturityDate(LocalDate.of(2026, 11, 20), 240)).isEqualTo("2046-11-19");
        assertThat(PremiumCalculator.maturityDate(LocalDate.of(2026, 11, 20), 12)).isEqualTo("2027-11-19");
        assertThat(PremiumCalculator.maturityDate(LocalDate.of(2028, 2, 29), 12)).isEqualTo("2029-02-27");
    }

    @Test
    void ageCountsCompletedYears() {
        LocalDate dob = LocalDate.of(1990, 4, 15);
        assertThat(PremiumCalculator.ageOn(dob, LocalDate.of(2026, 4, 14))).isEqualTo(35);
        assertThat(PremiumCalculator.ageOn(dob, LocalDate.of(2026, 4, 15))).isEqualTo(36);
        assertThatThrownBy(() -> PremiumCalculator.ageOn(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scheduleMatchesSpecSeedRows() {
        var schedule = PremiumCalculator.schedule(LocalDate.of(2026, 11, 20), PremiumFrequency.MONTHLY, 240,
                new BigDecimal("1250"), 1);

        assertThat(schedule).hasSize(240);
        assertThat(schedule.get(0)).isEqualTo(new PremiumCalculator.ScheduledInstallment(
                1, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 20), new BigDecimal("1250.00")));
        assertThat(schedule.get(2).dueDate()).isEqualTo("2027-01-20");
        assertThat(schedule.get(2).graceEndDate()).isEqualTo("2027-02-20");
        assertThat(schedule.getLast().dueDate()).isEqualTo("2046-10-20");
    }

    @Test
    void monthEndStartDatesDoNotDrift() {
        var schedule = PremiumCalculator.schedule(LocalDate.of(2027, 1, 31), PremiumFrequency.MONTHLY, 4,
                new BigDecimal("100"), 1);
        assertThat(schedule).extracting(PremiumCalculator.ScheduledInstallment::dueDate)
                .containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28),
                        LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
    }

    @Test
    void quarterlyScheduleStepsThreeMonths() {
        var schedule = PremiumCalculator.schedule(LocalDate.of(2026, 12, 1), PremiumFrequency.QUARTERLY, 120,
                new BigDecimal("7500"), 1);
        assertThat(schedule).hasSize(40);
        assertThat(schedule.get(1).dueDate()).isEqualTo("2027-03-01");
        assertThat(schedule.getLast().dueDate()).isEqualTo("2036-09-01");
    }
}
