package com.healthDekho.backend.service;

import com.healthDekho.backend.config.PaymentProperties;
import com.healthDekho.backend.dto.policy.PolicyRegistrationResponse;
import com.healthDekho.backend.dto.policy.RegisterPolicyRequest;
import com.healthDekho.backend.dto.policy.RegistrationPreviewResponse;
import com.healthDekho.backend.entity.AiRecommendationItem;
import com.healthDekho.backend.entity.Customer;
import com.healthDekho.backend.entity.CustomerPolicy;
import com.healthDekho.backend.entity.InsuranceProduct;
import com.healthDekho.backend.entity.PremiumInstallment;
import com.healthDekho.backend.entity.enums.PolicyStatus;
import com.healthDekho.backend.exception.BusinessRuleException;
import com.healthDekho.backend.exception.ResourceNotFoundException;
import com.healthDekho.backend.repository.AiRecommendationItemRepository;
import com.healthDekho.backend.repository.CustomerPolicyRepository;
import com.healthDekho.backend.repository.CustomerRepository;
import com.healthDekho.backend.repository.InsuranceProductRepository;
import com.healthDekho.backend.service.PremiumCalculator.ScheduledInstallment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** FR-3: pre-populated registration form, validation, policy creation and installment schedule. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyRegistrationService {

    private final CustomerRepository customerRepository;
    private final InsuranceProductRepository productRepository;
    private final CustomerPolicyRepository policyRepository;
    private final AiRecommendationItemRepository recommendationItemRepository;
    private final PolicyNumberGenerator policyNumberGenerator;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    /** Terms of a prospective policy, computed once and shared by preview and registration. */
    private record Quote(Customer customer, InsuranceProduct product, LocalDate startDate, LocalDate maturityDate,
                         int ageAtStart, int installments, BigDecimal totalPremium, Optional<String> problem) {
    }

    @Transactional(readOnly = true)
    public RegistrationPreviewResponse preview(long customerId, long productId, LocalDate requestedStart) {
        Quote q = quote(customerId, productId, requestedStart);
        Customer c = q.customer();
        InsuranceProduct p = q.product();
        return new RegistrationPreviewResponse(
                p.getProductId(), p.getProductCode(),
                c.getFullName(), c.getDateOfBirth(), c.getEmail(), c.getPhone(), q.ageAtStart(),
                p.getPolicyType(), p.getPolicyName(), p.getTermMonths(),
                q.startDate(), q.maturityDate(),
                p.getPremiumFrequency(), p.getPremiumAmount(), p.getSumInsured(),
                q.installments(), q.totalPremium(),
                q.problem().isEmpty(), q.problem().orElse(null));
    }

    @Transactional
    public PolicyRegistrationResponse register(long customerId, RegisterPolicyRequest request) {
        // Taken first: serializes registrations so the duplicate check below cannot race.
        String policyNumber = policyNumberGenerator.lockAndGenerateNext();

        Quote q = quote(customerId, request.productId(), request.startDate());
        q.problem().ifPresent(reason -> {
            throw new BusinessRuleException(reason);
        });
        Optional<AiRecommendationItem> recommendation = Optional.ofNullable(request.recommendationItemId())
                .map(itemId -> recommendationFor(customerId, itemId, q.product()));

        CustomerPolicy policy = newPolicy(policyNumber, q);
        List<ScheduledInstallment> schedule = PremiumCalculator.schedule(q.startDate(), q.product().getPremiumFrequency(),
                q.product().getTermMonths(), q.product().getPremiumAmount(), paymentProperties.gracePeriodMonths());
        schedule.forEach(s -> policy.addInstallment(toInstallment(s)));

        try {
            policyRepository.saveAndFlush(policy);
        } catch (DataIntegrityViolationException e) {
            // Backstop for the UNIQUE(customer_id, product_id) / UNIQUE(policy_number) constraints.
            throw new BusinessRuleException("You already have a policy for " + q.product().getPolicyName() + ".");
        }
        recommendation.ifPresent(item -> item.setSelectedForRegistration(true));

        log.info("Registered policy {} for customer {} (product {}, {} installments)",
                policyNumber, customerId, q.product().getProductCode(), schedule.size());

        return new PolicyRegistrationResponse(
                policy.getPolicyNumber(), q.product().getPolicyName(),
                policy.getStartDate(), policy.getMaturityDate(),
                policy.getPremiumFrequency(), policy.getPremiumAmount(), policy.getSumInsured(),
                policy.getTotalPremium(), schedule.size(), schedule.getFirst().dueDate(),
                policy.getStatus(),
                "Policy %s registered successfully.".formatted(policy.getPolicyNumber()));
    }

    // ---------------------------------------------------------------------------------------------

    private Quote quote(long customerId, long productId, LocalDate requestedStart) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        InsuranceProduct product = productRepository.findByProductIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance product not found or no longer available."));

        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = requestedStart != null ? requestedStart : today;

        // Hard errors: the form cannot be shown meaningfully.
        if (startDate.isBefore(today)) {
            throw new BusinessRuleException("Policy start date cannot be in the past.");
        }
        if (customer.getDateOfBirth().isAfter(today)) {
            throw new BusinessRuleException("The date of birth on your profile is in the future. Please contact support.");
        }
        if (product.getPremiumAmount().signum() <= 0 || product.getSumInsured().signum() <= 0
                || product.getTermMonths() <= 0) {
            throw new BusinessRuleException("This product has invalid pricing and cannot be registered.");
        }
        if (policyRepository.existsByCustomerCustomerIdAndProductProductId(customerId, productId)) {
            throw new BusinessRuleException("You already have a policy for " + product.getPolicyName() + ".");
        }

        LocalDate maturityDate = PremiumCalculator.maturityDate(startDate, product.getTermMonths());
        if (!maturityDate.isAfter(startDate)) {
            throw new BusinessRuleException("Policy maturity date must be after the start date.");
        }
        int installments = PremiumCalculator.numberOfInstallments(product.getPremiumFrequency(), product.getTermMonths());
        BigDecimal totalPremium = PremiumCalculator.totalPremium(
                product.getPremiumAmount(), product.getPremiumFrequency(), product.getTermMonths());

        // Soft error: the form is still shown, with the reason, but registration is refused.
        int age = PremiumCalculator.ageOn(customer.getDateOfBirth(), startDate);
        Optional<String> problem = product.acceptsEntryAge(age)
                ? Optional.empty()
                : Optional.of("%s is available for ages %d to %d on the start date. Your age on %s will be %d."
                        .formatted(product.getPolicyName(), product.getMinEntryAge(), product.getMaxEntryAge(),
                                startDate, age));

        return new Quote(customer, product, startDate, maturityDate, age, installments, totalPremium, problem);
    }

    private AiRecommendationItem recommendationFor(long customerId, long itemId, InsuranceProduct product) {
        AiRecommendationItem item = recommendationItemRepository
                .findByRecommendationItemIdAndRequestCustomerCustomerId(itemId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found."));
        if (!item.getProduct().getProductId().equals(product.getProductId())) {
            throw new BusinessRuleException("The recommendation does not match the selected product.");
        }
        return item;
    }

    private static CustomerPolicy newPolicy(String policyNumber, Quote q) {
        InsuranceProduct p = q.product();
        CustomerPolicy policy = new CustomerPolicy();
        policy.setPolicyNumber(policyNumber);
        policy.setCustomer(q.customer());
        policy.setProduct(p);
        // Terms are copied so later catalog changes never alter an existing contract.
        policy.setTermMonths(p.getTermMonths());
        policy.setStartDate(q.startDate());
        policy.setMaturityDate(q.maturityDate());
        policy.setPremiumFrequency(p.getPremiumFrequency());
        policy.setPremiumAmount(p.getPremiumAmount());
        policy.setSumInsured(p.getSumInsured());
        policy.setTotalPremium(q.totalPremium());
        policy.setStatus(PolicyStatus.ACTIVE);
        return policy;
    }

    private static PremiumInstallment toInstallment(ScheduledInstallment s) {
        PremiumInstallment installment = new PremiumInstallment();
        installment.setInstallmentNo(s.installmentNo());
        installment.setDueDate(s.dueDate());
        installment.setGraceEndDate(s.graceEndDate());
        installment.setScheduledAmount(s.amount());
        return installment;
    }
}
