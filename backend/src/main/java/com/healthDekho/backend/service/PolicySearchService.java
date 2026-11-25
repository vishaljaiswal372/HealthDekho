package com.healthDekho.backend.service;

import com.healthDekho.backend.config.PaymentProperties;
import com.healthDekho.backend.config.SearchProperties;
import com.healthDekho.backend.dto.PageResponse;
import com.healthDekho.backend.dto.search.PolicySearchRequest;
import com.healthDekho.backend.dto.search.PolicySearchResult;
import com.healthDekho.backend.dto.search.SearchCriteria;
import com.healthDekho.backend.dto.search.SearchCriteria.ByCustomerName;
import com.healthDekho.backend.dto.search.SearchCriteria.ByPolicyNumber;
import com.healthDekho.backend.dto.search.SearchCriteria.ByPolicyNumberAndDateOfBirth;
import com.healthDekho.backend.entity.CustomerPolicy;
import com.healthDekho.backend.entity.PremiumInstallment;
import com.healthDekho.backend.repository.CustomerPolicyRepository;
import com.healthDekho.backend.repository.PremiumInstallmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** FR-5: search policies by policy identifier, customer name, or policy identifier + date of birth. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicySearchService {

    static final String NO_MATCHES = "No policies match the search criteria.";

    private final CustomerPolicyRepository policyRepository;
    private final PremiumInstallmentRepository installmentRepository;
    private final SearchProperties searchProperties;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    public PageResponse<PolicySearchResult> search(PolicySearchRequest request) {
        SearchCriteria criteria = SearchCriteria.from(request);
        Pageable pageable = pageable(request);

        Page<CustomerPolicy> page = switch (criteria) {
            case ByPolicyNumber(String number) ->
                    single(policyRepository.findByPolicyNumberWithDetails(number), pageable);
            case ByPolicyNumberAndDateOfBirth(String number, LocalDate dob) ->
                    single(policyRepository.findByPolicyNumberAndCustomerDob(number, dob), pageable);
            case ByCustomerName(String name) ->
                    policyRepository.searchByCustomerName(likePattern(name), pageable);
        };

        Map<Long, BigDecimal> remaining = remainingPremiumByPolicy(page.getContent());
        List<PolicySearchResult> results = page.getContent().stream()
                .map(p -> toResult(p, remaining.getOrDefault(p.getPolicyId(), BigDecimal.ZERO)))
                .toList();

        return new PageResponse<>(results, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), results.isEmpty() ? NO_MATCHES : null);
    }

    /**
     * Outstanding premium (including late penalties as of today) for every policy on the page,
     * from one installments query grouped by policy.
     */
    private Map<Long, BigDecimal> remainingPremiumByPolicy(List<CustomerPolicy> policies) {
        if (policies.isEmpty()) return Map.of();
        LocalDate today = LocalDate.now(clock);
        BigDecimal rate = paymentProperties.latePenaltyRate();
        List<Long> ids = policies.stream().map(CustomerPolicy::getPolicyId).toList();

        return installmentRepository.findByPolicyPolicyIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        i -> i.getPolicy().getPolicyId(),
                        Collectors.reducing(BigDecimal.ZERO, i -> outstandingAsOf(i, today, rate), BigDecimal::add)));
    }

    private static BigDecimal outstandingAsOf(PremiumInstallment i, LocalDate today, BigDecimal rate) {
        BigDecimal penalty = PaymentCalculator.penaltyAsOf(i.getScheduledAmount(), i.getPaidAmount(),
                i.getPenaltyAmount(), i.getGraceEndDate(), today, rate);
        return PaymentCalculator.outstanding(i.getScheduledAmount(), penalty, i.getPaidAmount());
    }

    private Pageable pageable(PolicySearchRequest request) {
        int size = Math.min(Optional.ofNullable(request.size()).orElse(searchProperties.defaultPageSize()),
                searchProperties.maxPageSize());
        return PageRequest.of(Optional.ofNullable(request.page()).orElse(0), size);
    }

    /** Exact-match searches return at most one policy, presented as a page for a uniform response. */
    private static Page<CustomerPolicy> single(Optional<CustomerPolicy> match, Pageable pageable) {
        List<CustomerPolicy> content = pageable.getPageNumber() == 0 ? match.stream().toList() : List.of();
        return new PageImpl<>(content, pageable, match.isPresent() ? 1 : 0);
    }

    /** Contains-match, case-insensitive; the user's %, _ and \ are matched literally. */
    private static String likePattern(String name) {
        String escaped = name.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static PolicySearchResult toResult(CustomerPolicy p, BigDecimal remainingPremium) {
        return new PolicySearchResult(
                p.getPolicyNumber(), p.getCustomer().getFullName(), p.getCustomer().getDateOfBirth(),
                p.getProduct().getPolicyName(), p.getProduct().getPolicyType(),
                p.getStartDate(), p.getMaturityDate(),
                p.getPremiumFrequency(), p.getPremiumAmount(), p.getSumInsured(), p.getTotalPremium(),
                p.getStatus(), remainingPremium);
    }
}
