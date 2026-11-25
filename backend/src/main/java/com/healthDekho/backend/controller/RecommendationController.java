package com.healthDekho.backend.controller;

import com.healthDekho.backend.dto.recommendation.RecommendationRequest;
import com.healthDekho.backend.dto.recommendation.RecommendationResponse;
import com.healthDekho.backend.security.CurrentCustomer;
import com.healthDekho.backend.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-2 AI recommendations for the logged-in customer. */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final CurrentCustomer currentCustomer;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecommendationResponse recommend(@Valid @RequestBody RecommendationRequest request) {
        return recommendationService.recommend(currentCustomer.id(), request);
    }

    /** The customer's 10 most recent recommendation requests, newest first. */
    @GetMapping
    public List<RecommendationResponse> history() {
        return recommendationService.history(currentCustomer.id());
    }
}
