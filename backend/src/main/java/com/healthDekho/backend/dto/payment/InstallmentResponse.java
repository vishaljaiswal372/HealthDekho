package com.healthDekho.backend.dto.payment;

import com.healthDekho.backend.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * One row of the FR-4 premium schedule, as of today. {@code penaltyAmount} already includes a late
 * penalty that would apply if paid today, so {@code outstandingAmount} is exactly what is payable now.
 */
public record InstallmentResponse(
        Long installmentId,
        int installmentNo,
        YearMonth period,
        BigDecimal premiumAmount,
        LocalDate dueDate,
        LocalDate graceEndDate,
        BigDecimal penaltyAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        PaymentStatus paymentStatus,
        boolean payable) {
}
