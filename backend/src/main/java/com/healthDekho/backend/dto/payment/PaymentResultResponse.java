package com.healthDekho.backend.dto.payment;

import com.healthDekho.backend.entity.enums.PaymentMethod;
import com.healthDekho.backend.entity.enums.PaymentStatus;
import com.healthDekho.backend.entity.enums.PolicyStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Outcome of an FR-4 payment: one receipt per installment touched, plus the recalculated policy balance. */
public record PaymentResultResponse(
        String policyNumber,
        LocalDate paymentDate,
        BigDecimal amountPaid,
        PaymentMethod paymentMethod,
        BigDecimal penaltyApplied,
        List<Receipt> receipts,
        BigDecimal policyOutstandingAmount,
        PolicyStatus policyStatus,
        String message) {

    public record Receipt(
            String transactionReference,
            int installmentNo,
            BigDecimal amountApplied,
            BigDecimal penaltyAmount,
            BigDecimal installmentOutstanding,
            PaymentStatus installmentStatus) {
    }
}
