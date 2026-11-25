package com.healthDekho.backend.controller;

import com.healthDekho.backend.dto.payment.*;
import com.healthDekho.backend.security.CurrentCustomer;
import com.healthDekho.backend.service.PremiumPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-4 premium payment management. Customers can only see and pay their own policies. */
@RestController
@RequestMapping("/api/policies/{policyNumber}")
@RequiredArgsConstructor
public class PremiumPaymentController {

    private final PremiumPaymentService paymentService;
    private final CurrentCustomer currentCustomer;

    @GetMapping
    public PolicyDetailsResponse policy(@PathVariable String policyNumber) {
        return paymentService.policyDetails(currentCustomer.id(), policyNumber);
    }

    @GetMapping("/installments")
    public List<InstallmentResponse> installments(@PathVariable String policyNumber) {
        return paymentService.schedule(currentCustomer.id(), policyNumber);
    }

    @GetMapping("/payments")
    public List<PaymentHistoryResponse> payments(@PathVariable String policyNumber) {
        return paymentService.history(currentCustomer.id(), policyNumber);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResultResponse pay(@PathVariable String policyNumber,
                                     @Valid @RequestBody PremiumPaymentRequest request) {
        return paymentService.pay(currentCustomer.id(), policyNumber, request);
    }
}
