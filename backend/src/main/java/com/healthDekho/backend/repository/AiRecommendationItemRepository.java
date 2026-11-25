package com.healthDekho.backend.repository;

import com.healthDekho.backend.entity.AiRecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiRecommendationItemRepository extends JpaRepository<AiRecommendationItem, Long> {

    Optional<AiRecommendationItem> findByRecommendationItemIdAndRequestCustomerCustomerId(Long itemId, Long customerId);
}
