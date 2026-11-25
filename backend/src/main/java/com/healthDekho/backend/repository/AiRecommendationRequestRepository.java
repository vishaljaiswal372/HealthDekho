package com.healthDekho.backend.repository;

import com.healthDekho.backend.entity.AiRecommendationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiRecommendationRequestRepository extends JpaRepository<AiRecommendationRequest, Long> {

    List<AiRecommendationRequest> findByCustomerCustomerIdOrderByRequestedAtDesc(Long customerId);

    List<AiRecommendationRequest> findTop10ByCustomerCustomerIdOrderByRequestedAtDesc(Long customerId);
}
