package com.healthDekho.backend.controller;

import com.healthDekho.backend.dto.policy.PolicySummaryResponse;
import com.healthDekho.backend.dto.product.AvailableProductResponse;
import com.healthDekho.backend.security.CurrentCustomer;
import com.healthDekho.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** FR-1 dashboard. The customer is always taken from the token, never from the request. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentCustomer currentCustomer;

    @GetMapping("/policies")
    public List<PolicySummaryResponse> myPolicies() {
        return dashboardService.policiesOf(currentCustomer.id());
    }

    @GetMapping("/available-products")
    public List<AvailableProductResponse> availableProducts() {
        return dashboardService.availableProductsFor(currentCustomer.id());
    }
}
