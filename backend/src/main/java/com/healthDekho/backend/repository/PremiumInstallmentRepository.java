package com.healthDekho.backend.repository;

import com.healthDekho.backend.entity.PremiumInstallment;
import com.healthDekho.backend.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface PremiumInstallmentRepository extends JpaRepository<PremiumInstallment, Long> {

    List<PremiumInstallment> findByPolicyPolicyIdOrderByInstallmentNoAsc(Long policyId);

    List<PremiumInstallment> findByPolicyPolicyIdIn(Collection<Long> policyIds);

    List<PremiumInstallment> findByPolicyPolicyIdAndInstallmentIdIn(Long policyId, Collection<Long> installmentIds);

    boolean existsByPolicyPolicyIdAndPaymentStatusNot(Long policyId, PaymentStatus status);

    /** Remaining premium for a policy (scheduled + penalty - paid), used in FR-5 search results. */
    @Query("""
            select coalesce(sum(i.scheduledAmount + i.penaltyAmount - i.paidAmount), 0)
            from PremiumInstallment i
            where i.policy.policyId = :policyId
            """)
    BigDecimal sumOutstandingByPolicyId(@Param("policyId") Long policyId);
}
