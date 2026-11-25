package com.healthDekho.backend.dto.payment;

import com.healthDekho.backend.entity.PremiumPayment;
import com.healthDekho.backend.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentHistoryResponse(
        String transactionReference,
        int installmentNo,
        LocalDate paymentDate,
        BigDecimal amountPaid,
        BigDecimal penaltyAmount,
        PaymentMethod paymentMethod) {

    public static PaymentHistoryResponse from(PremiumPayment p) {
        return new PaymentHistoryResponse(p.getTransactionReference(), p.getInstallment().getInstallmentNo(),
                p.getPaymentDate(), p.getAmountPaid(), p.getPenaltyAmount(), p.getPaymentMethod());
    }
}
