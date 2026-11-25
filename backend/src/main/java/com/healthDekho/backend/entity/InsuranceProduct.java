package com.healthDekho.backend.entity;

import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.entity.enums.PremiumFrequency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Master catalog entry. Customer policies copy these terms at registration. */
@Entity
@Table(name = "insurance_product")
@Getter
@Setter
@NoArgsConstructor
public class InsuranceProduct extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_code", nullable = false, unique = true, length = 30)
    private String productCode;

    @Column(name = "policy_name", nullable = false, length = 150)
    private String policyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 20)
    private PolicyType policyType;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "premium_frequency", nullable = false, length = 20)
    private PremiumFrequency premiumFrequency;

    @Column(name = "premium_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "sum_insured", nullable = false, precision = 19, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "min_entry_age", nullable = false)
    private Integer minEntryAge;

    @Column(name = "max_entry_age", nullable = false)
    private Integer maxEntryAge;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public boolean acceptsEntryAge(int age) {
        return age >= minEntryAge && age <= maxEntryAge;
    }
}
