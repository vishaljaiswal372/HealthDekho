package com.healthDekho.backend.service;

import com.healthDekho.backend.ai.RecommendationModel;
import com.healthDekho.backend.ai.RecommendationModel.Candidate;
import com.healthDekho.backend.ai.RecommendationModel.ModelResult;
import com.healthDekho.backend.ai.RecommendationModel.Pick;
import com.healthDekho.backend.config.AiProperties;
import com.healthDekho.backend.dto.recommendation.RecommendationRequest;
import com.healthDekho.backend.dto.recommendation.RecommendationResponse;
import com.healthDekho.backend.entity.AiRecommendationItem;
import com.healthDekho.backend.entity.AiRecommendationRequest;
import com.healthDekho.backend.entity.Customer;
import com.healthDekho.backend.entity.InsuranceProduct;
import com.healthDekho.backend.entity.enums.Suitability;
import com.healthDekho.backend.exception.InvalidRequestException;
import com.healthDekho.backend.exception.ResourceNotFoundException;
import com.healthDekho.backend.repository.AiRecommendationRequestRepository;
import com.healthDekho.backend.repository.CustomerRepository;
import com.healthDekho.backend.repository.InsuranceProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FR-2: AI-ranked recommendations restricted to products in the database.
 * <p>
 * Deterministic rules run before and after the model: candidates are active products the customer
 * does not hold and is old enough for; whatever the model returns is checked against that list,
 * de-duplicated, capped and re-ranked. Product figures always come from the database.
 */
@Slf4j
@Service
public class RecommendationService {

    static final int MAX_REASON_LENGTH = 1000;

    private final CustomerRepository customerRepository;
    private final InsuranceProductRepository productRepository;
    private final AiRecommendationRequestRepository requestRepository;
    private final RecommendationModel model;
    private final AiProperties aiProperties;
    private final Clock clock;
    private final TransactionTemplate readOnlyTx;
    private final TransactionTemplate writeTx;

    public RecommendationService(CustomerRepository customerRepository, InsuranceProductRepository productRepository,
                                 AiRecommendationRequestRepository requestRepository, RecommendationModel model,
                                 AiProperties aiProperties, Clock clock, PlatformTransactionManager txManager) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.requestRepository = requestRepository;
        this.model = model;
        this.aiProperties = aiProperties;
        this.clock = clock;
        this.readOnlyTx = new TransactionTemplate(txManager);
        this.readOnlyTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    private record Context(Customer customer, int age, List<InsuranceProduct> candidates) {
    }

    /** Not @Transactional on purpose: no database transaction is held open while the model is called. */
    public RecommendationResponse recommend(long customerId, RecommendationRequest request) {
        if (!request.hasAnyInput()) {
            throw new InvalidRequestException(
                    "Provide at least one of: date of birth, insurance goals, preferred term or insurance type.");
        }

        // 1. Deterministic candidate list (short read-only transaction).
        Context ctx = readOnlyTx.execute(status -> {
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
            LocalDate dob = request.dateOfBirth() != null ? request.dateOfBirth() : customer.getDateOfBirth();
            int age = PremiumCalculator.ageOn(dob, LocalDate.now(clock));
            List<InsuranceProduct> candidates = productRepository.findAvailableForCustomer(customerId).stream()
                    .filter(p -> p.acceptsEntryAge(age))
                    .toList();
            return new Context(customer, age, candidates);
        });

        if (ctx.candidates().isEmpty()) {
            return new RecommendationResponse(null, ctx.age(), null, List.of(),
                    "There are no further products you are eligible for right now.");
        }

        // 2. AI ranking (no transaction). AiUnavailableException propagates as 503; nothing is saved.
        ModelResult result = model.recommend(new RecommendationModel.Input(
                ctx.age(), blankToNull(request.insuranceGoals()), request.preferredTermMonths(),
                request.preferredPolicyType(), aiProperties.maxRecommendations(),
                ctx.candidates().stream().map(RecommendationService::toCandidate).toList()));

        List<ValidPick> picks = validate(result.picks(), ctx.candidates());
        if (picks.size() < result.picks().size()) {
            log.warn("Discarded {} of {} AI picks that failed validation", result.picks().size() - picks.size(),
                    result.picks().size());
        }

        // 3. Persist request + items (short write transaction).
        return writeTx.execute(status -> persist(customerId, request, ctx.age(), result.modelName(), picks));
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> history(long customerId) {
        return requestRepository.findTop10ByCustomerCustomerIdOrderByRequestedAtDesc(customerId).stream()
                .map(RecommendationService::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------- validation of model output

    record ValidPick(InsuranceProduct product, int rank, Suitability suitability, String reason) {
    }

    /**
     * Keeps only picks that name a candidate product, have a known suitability and a reason;
     * first occurrence wins; ordered by the model's rank; capped; then re-ranked 1..n.
     */
    List<ValidPick> validate(List<Pick> picks, List<InsuranceProduct> candidates) {
        Map<String, InsuranceProduct> byCode = candidates.stream()
                .collect(Collectors.toMap(p -> p.getProductCode().toUpperCase(Locale.ROOT), Function.identity()));
        Set<String> seen = new HashSet<>();

        List<ValidPick> valid = picks.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Pick::rank, Comparator.nullsLast(Comparator.naturalOrder())))
                .flatMap(p -> {
                    String code = p.productCode() == null ? "" : p.productCode().strip().toUpperCase(Locale.ROOT);
                    InsuranceProduct product = byCode.get(code);
                    Optional<Suitability> suitability = parseSuitability(p.suitability());
                    String reason = p.reason() == null ? "" : p.reason().strip();
                    if (product == null || suitability.isEmpty() || reason.isEmpty() || !seen.add(code)) {
                        return java.util.stream.Stream.empty();
                    }
                    return java.util.stream.Stream.of(new ValidPick(product, 0, suitability.get(), truncate(reason)));
                })
                .limit(aiProperties.maxRecommendations())
                .toList();

        List<ValidPick> ranked = new ArrayList<>();
        for (int i = 0; i < valid.size(); i++) {
            ValidPick v = valid.get(i);
            ranked.add(new ValidPick(v.product(), i + 1, v.suitability(), v.reason()));
        }
        return ranked;
    }

    private static Optional<Suitability> parseSuitability(String value) {
        if (value == null) return Optional.empty();
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> Optional.of(Suitability.HIGH);
            case "MEDIUM" -> Optional.of(Suitability.MEDIUM);
            case "LOW" -> Optional.of(Suitability.LOW);
            default -> Optional.empty();
        };
    }

    // ---------------------------------------------------------------- persistence & mapping

    private RecommendationResponse persist(long customerId, RecommendationRequest request, int age, String modelName,
                                           List<ValidPick> picks) {
        AiRecommendationRequest entity = new AiRecommendationRequest();
        entity.setCustomer(customerRepository.getReferenceById(customerId));
        entity.setInsuranceGoals(blankToNull(request.insuranceGoals()));
        entity.setPreferredTermMonths(request.preferredTermMonths());
        entity.setPreferredPolicyType(request.preferredPolicyType());
        entity.setCalculatedAge(age);
        entity.setModelName(modelName);
        entity.setModelVersion(aiProperties.modelVersion());
        entity.setPromptVersion(aiProperties.promptVersion());

        for (ValidPick pick : picks) {
            // Product must still exist and be active at the moment the recommendation is stored.
            productRepository.findByProductIdAndActiveTrue(pick.product().getProductId()).ifPresent(product -> {
                AiRecommendationItem item = new AiRecommendationItem();
                item.setProduct(product);
                item.setRankOrder(pick.rank());
                item.setSuitability(pick.suitability());
                item.setRecommendationReason(pick.reason());
                entity.addItem(item);
            });
        }
        requestRepository.saveAndFlush(entity);
        return toResponse(entity);
    }

    private static RecommendationResponse toResponse(AiRecommendationRequest r) {
        List<RecommendationResponse.Item> items = r.getItems().stream()
                .map(i -> {
                    InsuranceProduct p = i.getProduct();
                    return new RecommendationResponse.Item(
                            i.getRecommendationItemId(), i.getRankOrder(), p.getProductId(), p.getProductCode(),
                            p.getPolicyName(), p.getPolicyType(), p.getTermMonths(), p.getSumInsured(),
                            p.getPremiumFrequency(), p.getPremiumAmount(), i.getRecommendationReason(),
                            i.getSuitability(), i.isSelectedForRegistration());
                })
                .toList();
        return new RecommendationResponse(r.getRecommendationRequestId(), r.getCalculatedAge(), r.getRequestedAt(),
                items, items.isEmpty() ? "No product in our catalogue is a good fit for the details you gave." : null);
    }

    private static Candidate toCandidate(InsuranceProduct p) {
        return new Candidate(p.getProductCode(), p.getPolicyName(), p.getPolicyType(), p.getTermMonths(),
                p.getPremiumFrequency(), p.getPremiumAmount(), p.getSumInsured());
    }

    private static String truncate(String reason) {
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH - 1) + "…";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
