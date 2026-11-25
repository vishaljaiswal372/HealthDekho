package com.healthDekho.backend.repository;

import com.healthDekho.backend.entity.PremiumPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PremiumPaymentRepository extends JpaRepository<PremiumPayment, Long> {

    boolean existsByTransactionReference(String transactionReference);

    List<PremiumPayment> findByInstallmentPolicyPolicyIdOrderByPaymentDateAscPaymentIdAsc(Long policyId);
}
