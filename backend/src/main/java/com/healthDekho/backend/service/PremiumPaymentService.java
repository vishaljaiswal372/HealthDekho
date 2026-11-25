package com.healthDekho.backend.service;

import com.healthDekho.backend.config.PaymentProperties;
import com.healthDekho.backend.dto.payment.*;
import com.healthDekho.backend.dto.payment.PaymentResultResponse.Receipt;
import com.healthDekho.backend.entity.CustomerPolicy;
import com.healthDekho.backend.entity.PremiumInstallment;
import com.healthDekho.backend.entity.PremiumPayment;
import com.healthDekho.backend.entity.enums.PaymentStatus;
import com.healthDekho.backend.entity.enums.PolicyStatus;
import com.healthDekho.backend.exception.BusinessRuleException;
import com.healthDekho.backend.exception.ResourceNotFoundException;
import com.healthDekho.backend.repository.CustomerPolicyRepository;
import com.healthDekho.backend.repository.PremiumInstallmentRepository;
import com.healthDekho.backend.repository.PremiumPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** FR-4: policy search, premium schedule, payment processing and payment history. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PremiumPaymentService {

    private final CustomerPolicyRepository policyRepository;
    private final PremiumInstallmentRepository installmentRepository;
    private final PremiumPaymentRepository paymentRepository;
    private final PaymentReferenceGenerator referenceGenerator;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    /** An installment's figures as of a given date (penalty included). */
    private record InstallmentView(PremiumInstallment installment, BigDecimal penalty, BigDecimal outstanding,
                                   PaymentStatus status) {
    }

    /** How much of a payment goes to one installment, worked out before anything is changed. */
    private record Allocation(PremiumInstallment installment, BigDecimal penalty, BigDecimal newPenalty,
                              BigDecimal outstanding, BigDecimal applied) {
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public PolicyDetailsResponse policyDetails(long customerId, String policyNumber) {
        CustomerPolicy policy = ownedPolicy(customerId, policyNumber);
        List<InstallmentView> views = viewsOf(policy, LocalDate.now(clock));

        BigDecimal totalPaid = sum(views.stream().map(v -> v.installment().getPaidAmount()).toList());
        BigDecimal totalPenalty = sum(views.stream().map(InstallmentView::penalty).toList());
        BigDecimal outstanding = sum(views.stream().map(InstallmentView::outstanding).toList());
        int paid = (int) views.stream().filter(v -> v.status() == PaymentStatus.PAID).count();

        return new PolicyDetailsResponse(
                policy.getPolicyNumber(), policy.getCustomer().getFullName(),
                policy.getProduct().getPolicyName(), policy.getProduct().getPolicyType(),
                policy.getTermMonths(), policy.getStartDate(), policy.getMaturityDate(),
                policy.getPremiumFrequency(), policy.getPremiumAmount(), policy.getSumInsured(),
                policy.getTotalPremium(), totalPaid, totalPenalty, outstanding,
                paid, views.size(), policy.getStatus());
    }

    @Transactional(readOnly = true)
    public List<InstallmentResponse> schedule(long customerId, String policyNumber) {
        CustomerPolicy policy = ownedPolicy(customerId, policyNumber);
        return viewsOf(policy, LocalDate.now(clock)).stream()
                .map(v -> {
                    PremiumInstallment i = v.installment();
                    return new InstallmentResponse(
                            i.getInstallmentId(), i.getInstallmentNo(), YearMonth.from(i.getDueDate()),
                            i.getScheduledAmount(), i.getDueDate(), i.getGraceEndDate(),
                            v.penalty(), i.getPaidAmount(), v.outstanding(), v.status(),
                            policy.getStatus() == PolicyStatus.ACTIVE && PaymentCalculator.isPayable(v.status()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> history(long customerId, String policyNumber) {
        CustomerPolicy policy = ownedPolicy(customerId, policyNumber);
        return paymentRepository.findByInstallmentPolicyPolicyIdOrderByPaymentDateAscPaymentIdAsc(policy.getPolicyId())
                .stream()
                .map(PaymentHistoryResponse::from)
                .toList();
    }

    // ---------------------------------------------------------------- payment

    @Transactional
    public PaymentResultResponse pay(long customerId, String policyNumber, PremiumPaymentRequest request) {
        List<Long> ids = request.installmentIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessRuleException("Each installment can be selected only once.");
        }

        // Row lock on the policy: concurrent payments on the same policy are processed one at a time.
        CustomerPolicy policy = policyRepository.findByPolicyNumberForUpdate(policyNumber)
                .filter(p -> p.getCustomer().getCustomerId() == customerId)
                .orElseThrow(PremiumPaymentService::policyNotFound);
        switch (policy.getStatus()) {
            case ACTIVE -> { }
            case FULLY_PAID -> throw new BusinessRuleException("This policy is already fully paid.");
            case MATURED, CANCELLED -> throw new BusinessRuleException(
                    "Payments are not accepted for a %s policy.".formatted(policy.getStatus().name().toLowerCase()));
        }

        List<PremiumInstallment> selected = installmentRepository
                .findByPolicyPolicyIdAndInstallmentIdIn(policy.getPolicyId(), ids).stream()
                .sorted(Comparator.comparing(PremiumInstallment::getInstallmentNo))
                .toList();
        if (selected.size() != ids.size()) {
            throw new ResourceNotFoundException("One or more selected installments do not belong to this policy.");
        }

        LocalDate today = LocalDate.now(clock);
        BigDecimal rate = paymentProperties.latePenaltyRate();

        // 1. Plan the whole payment without touching any entity: penalties as of today, then
        //    allocate the amount oldest first. Any rule violation leaves the data untouched.
        BigDecimal amount = request.amountPaid();
        List<Allocation> plan = new ArrayList<>();
        BigDecimal remaining = amount;
        for (PremiumInstallment i : selected) {
            if (i.getPaidAmount().compareTo(i.getScheduledAmount().add(i.getPenaltyAmount())) >= 0) {
                throw new BusinessRuleException("Installment #%d is already paid.".formatted(i.getInstallmentNo()));
            }
            BigDecimal penalty = PaymentCalculator.penaltyAsOf(i.getScheduledAmount(), i.getPaidAmount(),
                    i.getPenaltyAmount(), i.getGraceEndDate(), today, rate);
            BigDecimal outstanding = PaymentCalculator.outstanding(i.getScheduledAmount(), penalty, i.getPaidAmount());
            BigDecimal applied = remaining.min(outstanding);
            remaining = remaining.subtract(applied);
            plan.add(new Allocation(i, penalty, penalty.subtract(i.getPenaltyAmount()), outstanding, applied));
        }

        BigDecimal totalOutstanding = sum(plan.stream().map(Allocation::outstanding).toList());
        if (amount.compareTo(totalOutstanding) > 0) {
            throw new BusinessRuleException(
                    "The amount exceeds the outstanding %s for the selected installments.".formatted(totalOutstanding));
        }
        plan.stream().filter(a -> a.applied().signum() == 0).findFirst().ifPresent(a -> {
            throw new BusinessRuleException("The amount does not reach installment #%d. Deselect it or increase the amount."
                    .formatted(a.installment().getInstallmentNo()));
        });

        // 2. Apply the plan: update installments and write one immutable payment row per installment.
        List<String> references = referenceGenerator.lockAndReserve(plan.size());
        List<PremiumPayment> payments = new ArrayList<>();
        List<Receipt> receipts = new ArrayList<>();
        for (int n = 0; n < plan.size(); n++) {
            Allocation a = plan.get(n);
            PremiumInstallment i = a.installment();
            i.setPenaltyAmount(a.penalty());
            i.setPaidAmount(i.getPaidAmount().add(a.applied()));
            i.setPaymentStatus(PaymentCalculator.statusAsOf(
                    i.getScheduledAmount().add(a.penalty()), i.getPaidAmount(), i.getGraceEndDate(), today));

            PremiumPayment payment = new PremiumPayment();
            payment.setInstallment(i);
            payment.setTransactionReference(references.get(n));
            payment.setPaymentDate(today);
            payment.setAmountPaid(a.applied());
            payment.setPenaltyAmount(a.newPenalty());
            payment.setPaymentMethod(request.paymentMethod());
            payments.add(payment);

            receipts.add(new Receipt(references.get(n), i.getInstallmentNo(), a.applied(), a.penalty(),
                    i.outstandingAmount(), i.getPaymentStatus()));
        }
        paymentRepository.saveAll(payments);

        // 3. The policy is fully paid only when every installment is.
        if (!installmentRepository.existsByPolicyPolicyIdAndPaymentStatusNot(policy.getPolicyId(), PaymentStatus.PAID)) {
            policy.setStatus(PolicyStatus.FULLY_PAID);
        }

        BigDecimal penaltyApplied = sum(plan.stream().map(Allocation::newPenalty).toList());
        BigDecimal policyOutstanding = sum(viewsOf(policy, today).stream().map(InstallmentView::outstanding).toList());
        log.info("Recorded payment of {} on policy {} across {} installment(s), penalty {}",
                amount, policyNumber, selected.size(), penaltyApplied);

        return new PaymentResultResponse(policyNumber, today, amount, request.paymentMethod(), penaltyApplied,
                receipts, policyOutstanding, policy.getStatus(),
                "Payment of %s recorded for %d installment(s).".formatted(amount, selected.size()));
    }

    // ---------------------------------------------------------------- helpers

    private CustomerPolicy ownedPolicy(long customerId, String policyNumber) {
        // Another customer's policy is reported as not found, so policy numbers can't be probed.
        return policyRepository.findByPolicyNumberWithDetails(policyNumber.trim())
                .filter(p -> p.getCustomer().getCustomerId() == customerId)
                .orElseThrow(PremiumPaymentService::policyNotFound);
    }

    private List<InstallmentView> viewsOf(CustomerPolicy policy, LocalDate onDate) {
        BigDecimal rate = paymentProperties.latePenaltyRate();
        return installmentRepository.findByPolicyPolicyIdOrderByInstallmentNoAsc(policy.getPolicyId()).stream()
                .map(i -> {
                    BigDecimal penalty = PaymentCalculator.penaltyAsOf(i.getScheduledAmount(), i.getPaidAmount(),
                            i.getPenaltyAmount(), i.getGraceEndDate(), onDate, rate);
                    BigDecimal due = i.getScheduledAmount().add(penalty);
                    return new InstallmentView(i, penalty,
                            PaymentCalculator.outstanding(i.getScheduledAmount(), penalty, i.getPaidAmount()),
                            PaymentCalculator.statusAsOf(due, i.getPaidAmount(), i.getGraceEndDate(), onDate));
                })
                .toList();
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static ResourceNotFoundException policyNotFound() {
        return new ResourceNotFoundException("No policy found with that policy number.");
    }
}
