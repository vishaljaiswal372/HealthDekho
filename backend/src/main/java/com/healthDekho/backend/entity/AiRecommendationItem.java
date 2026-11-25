package com.healthDekho.backend.entity;

import com.healthDekho.backend.entity.enums.Suitability;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A catalog product returned for a recommendation request, with the AI rationale. */
@Entity
@Table(name = "ai_recommendation_item",
        uniqueConstraints = @UniqueConstraint(name = "uq_rec_item_request_product",
                columnNames = {"recommendation_request_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AiRecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_item_id")
    private Long recommendationItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_request_id", nullable = false)
    private AiRecommendationRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct product;

    @Column(name = "rank_order", nullable = false)
    private Integer rankOrder;

    @Column(name = "recommendation_reason", nullable = false, columnDefinition = "text")
    private String recommendationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "suitability", nullable = false, length = 10)
    private Suitability suitability;

    @Column(name = "selected_for_registration", nullable = false)
    private boolean selectedForRegistration;
}
