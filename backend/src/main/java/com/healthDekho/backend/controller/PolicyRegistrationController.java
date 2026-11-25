package com.healthDekho.backend.controller;

import com.healthDekho.backend.dto.policy.PolicyRegistrationResponse;
import com.healthDekho.backend.dto.policy.RegisterPolicyRequest;
import com.healthDekho.backend.dto.policy.RegistrationPreviewResponse;
import com.healthDekho.backend.security.CurrentCustomer;
import com.healthDekho.backend.service.PolicyRegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** FR-3 policy registration for the logged-in customer. */
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyRegistrationController {

    private final PolicyRegistrationService registrationService;
    private final CurrentCustomer currentCustomer;

    /** Pre-populated form for the Register button (from available products or AI recommendations). */
    @GetMapping("/registration-preview")
    public RegistrationPreviewResponse preview(
            @RequestParam @Positive long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return registrationService.preview(currentCustomer.id(), productId, startDate);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyRegistrationResponse register(@Valid @RequestBody RegisterPolicyRequest request) {
        return registrationService.register(currentCustomer.id(), request);
    }
}
