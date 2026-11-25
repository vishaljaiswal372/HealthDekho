package com.healthDekho.backend.controller;

import com.healthDekho.backend.dto.PageResponse;
import com.healthDekho.backend.dto.search.PolicySearchRequest;
import com.healthDekho.backend.dto.search.PolicySearchResult;
import com.healthDekho.backend.service.PolicySearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-5 customer insurance policy search.
 * GET /api/policy-search?policyNumber=  |  ?customerName=&page=&size=  |  ?policyNumber=&dateOfBirth=
 */
@RestController
@RequestMapping("/api/policy-search")
@RequiredArgsConstructor
public class PolicySearchController {

    private final PolicySearchService searchService;

    @GetMapping
    public PageResponse<PolicySearchResult> search(@Valid @ModelAttribute PolicySearchRequest request) {
        return searchService.search(request);
    }
}
