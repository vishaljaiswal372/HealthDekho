package com.healthDekho.backend.entity;

import com.healthDekho.backend.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One scheduled premium of a policy. */
@Entity
@Table(name = "premium_installment",
        uniqueConstraints = @UniqueConstraint(name = "uq_installment_policy_no",
                columnNames = {"policy_id", "installment_no"}))
@Getter
@Setter
@NoArgsConstructor
public class PremiumInstallment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "installment_id")
    private Long installmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private CustomerPolicy policy;

    @Column(name = "installment_no", nullable = false)
    private Integer installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "grace_end_date", nullable = false)
    private LocalDate graceEndDate;

    @Column(name = "scheduled_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal scheduledAmount;

    /** Late penalty applied to this installment (0 unless paid after the grace period). */
    @Column(name = "penalty_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @OneToMany(mappedBy = "installment")
    @OrderBy("paymentDate ASC, paymentId ASC")
    private List<PremiumPayment> payments = new ArrayList<>();

    /** Scheduled amount + penalty - already paid. */
    public BigDecimal outstandingAmount() {
        return scheduledAmount.add(penaltyAmount).subtract(paidAmount);
    }
}
