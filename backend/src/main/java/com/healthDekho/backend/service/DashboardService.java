package com.healthDekho.backend.service;

import com.healthDekho.backend.dto.policy.PolicySummaryResponse;
import com.healthDekho.backend.dto.product.AvailableProductResponse;
import com.healthDekho.backend.repository.CustomerPolicyRepository;
import com.healthDekho.backend.repository.InsuranceProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** FR-1: the logged-in customer's policies, and the active products they have not registered yet. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final CustomerPolicyRepository policyRepository;
    private final InsuranceProductRepository productRepository;

    public List<PolicySummaryResponse> policiesOf(long customerId) {
        return policyRepository.findByCustomerIdWithProduct(customerId).stream()
                .map(PolicySummaryResponse::from)
                .toList();
    }

    public List<AvailableProductResponse> availableProductsFor(long customerId) {
        return productRepository.findAvailableForCustomer(customerId).stream()
                .map(AvailableProductResponse::from)
                .toList();
    }
}
