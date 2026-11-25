package com.healthDekho.backend.entity;

import com.healthDekho.backend.entity.enums.PolicyType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** One recommendation interaction: the customer inputs plus model/prompt metadata for auditing. */
@Entity
@Table(name = "ai_recommendation_request")
@Getter
@Setter
@NoArgsConstructor
public class AiRecommendationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_request_id")
    private Long recommendationRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "insurance_goals", columnDefinition = "text")
    private String insuranceGoals;

    @Column(name = "preferred_term_months")
    private Integer preferredTermMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_policy_type", length = 20)
    private PolicyType preferredPolicyType;

    @Column(name = "calculated_age", nullable = false)
    private Integer calculatedAge;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rankOrder ASC")
    private List<AiRecommendationItem> items = new ArrayList<>();

    public void addItem(AiRecommendationItem item) {
        items.add(item);
        item.setRequest(this);
    }
}
