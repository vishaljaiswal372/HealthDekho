package com.healthDekho.backend.dto.payment;

import com.healthDekho.backend.entity.enums.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pay one or more installments. The amount is applied oldest installment first; the last one
 * reached may be paid partially. The payment date is always the server's current date.
 */
public record PremiumPaymentRequest(
        @NotEmpty(message = "Select at least one installment")
        @Size(max = 60, message = "Select at most 60 installments at a time")
        List<@NotNull @Positive Long> installmentIds,

        @NotNull(message = "Enter the amount paid")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amountPaid,

        @NotNull(message = "Select a payment method")
        PaymentMethod paymentMethod) {
}
