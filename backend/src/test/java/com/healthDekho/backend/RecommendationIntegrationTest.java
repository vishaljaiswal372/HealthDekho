package com.healthDekho.backend;

import com.healthDekho.backend.ai.AiUnavailableException;
import com.healthDekho.backend.ai.RecommendationModel;
import com.healthDekho.backend.ai.RecommendationModel.Candidate;
import com.healthDekho.backend.ai.RecommendationModel.Input;
import com.healthDekho.backend.ai.RecommendationModel.ModelResult;
import com.healthDekho.backend.ai.RecommendationModel.Pick;
import com.healthDekho.backend.entity.AiRecommendationRequest;
import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.repository.AiRecommendationItemRepository;
import com.healthDekho.backend.repository.AiRecommendationRequestRepository;
import com.healthDekho.backend.support.MutableClock;
import com.healthDekho.backend.support.TestClockConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR-2 end to end with the AI model mocked (no API key needed). Aarav (1001, born 1990-04-15) holds
 * HD-TERM-001, so his candidates are HD-HEALTH-001 (ages 18-65) and HD-SAV-001 (ages 21-55).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestClockConfig.class)
class RecommendationIntegrationTest {

    private static final String SEED_PASSWORD = "Dekho@Dev2026";
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 1);

    @MockitoBean RecommendationModel model;
    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @Autowired AiRecommendationRequestRepository requests;
    @Autowired AiRecommendationItemRepository items;

    private String aarav;

    @BeforeEach
    void login() throws Exception {
        clock.reset();
        aarav = tokenFor("aarav.sharma@example.com");
        clock.setDate(TODAY);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private String tokenFor(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, SEED_PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions recommend(String token, String json) throws Exception {
        return mvc.perform(post("/api/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Input capturedInput() {
        var captor = ArgumentCaptor.forClass(Input.class);
        verify(model).recommend(captor.capture());
        return captor.getValue();
    }

    @Test
    void recommendationsAreRestrictedValidatedAndSaved() throws Exception {
        when(model.recommend(any())).thenReturn(new ModelResult("anthropic.claude-test", List.of(
                new Pick("HD-FAKE-999", 1, "HIGH", "Invented product"),             // not in DB
                new Pick("HD-TERM-001", 2, "HIGH", "Already owned"),                // not a candidate
                new Pick("hd-sav-001", 3, "high", "Savings match your wealth goal."),
                new Pick("HD-SAV-001", 4, "LOW", "Duplicate"),                      // duplicate
                new Pick("HD-HEALTH-001", 5, "MAYBE", "Bad suitability"),           // invalid suitability
                new Pick("HD-HEALTH-001", 6, "MEDIUM", "Health cover for the family."))));
        long before = requests.count();

        String body = recommend(aarav, """
                {"insuranceGoals": "Family income protection", "preferredTermMonths": 240, "preferredPolicyType": "TERM"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId", notNullValue()))
                .andExpect(jsonPath("$.calculatedAge").value(36))
                .andExpect(jsonPath("$.recommendations", hasSize(2)))
                .andExpect(jsonPath("$.recommendations[0].rank").value(1))
                .andExpect(jsonPath("$.recommendations[0].productCode").value("HD-SAV-001"))
                .andExpect(jsonPath("$.recommendations[0].suitability").value("HIGH"))
                .andExpect(jsonPath("$.recommendations[0].recommendationReason").value("Savings match your wealth goal."))
                // product figures come from the database
                .andExpect(jsonPath("$.recommendations[0].premiumAmount").value(7500.00))
                .andExpect(jsonPath("$.recommendations[0].termMonths").value(120))
                .andExpect(jsonPath("$.recommendations[1].rank").value(2))
                .andExpect(jsonPath("$.recommendations[1].productCode").value("HD-HEALTH-001"))
                .andExpect(jsonPath("$.recommendations[1].suitability").value("MEDIUM"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // the model only saw eligible, not-yet-owned products and no identifying data
        Input input = capturedInput();
        assertThat(input.candidates()).extracting(Candidate::productCode)
                .containsExactlyInAnyOrder("HD-HEALTH-001", "HD-SAV-001");
        assertThat(input.age()).isEqualTo(36);
        assertThat(input.preferredPolicyType()).isEqualTo(PolicyType.TERM);
        assertThat(input.maxRecommendations()).isEqualTo(3);

        // saved for audit
        assertThat(requests.count()).isEqualTo(before + 1);
        long requestId = JsonPath.<Number>read(body, "$.requestId").longValue();
        AiRecommendationRequest saved = requests.findById(requestId).orElseThrow();
        assertThat(saved.getCalculatedAge()).isEqualTo(36);
        assertThat(saved.getModelName()).isEqualTo("anthropic.claude-test");
        assertThat(saved.getModelVersion()).isEqualTo("deployment-v1");
        assertThat(saved.getPromptVersion()).isEqualTo("rec-v1");
        assertThat(saved.getPreferredTermMonths()).isEqualTo(240);
        assertThat(saved.getItems()).hasSize(2);
    }

    @Test
    void providedDateOfBirthDrivesAgeAndEligibility() throws Exception {
        when(model.recommend(any())).thenReturn(new ModelResult("m", List.of()));

        // 58 on 2026-12-01: too old for Smart Savings (21-55), fine for Family Health (18-65)
        recommend(aarav, "{\"dateOfBirth\": \"1968-06-01\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.calculatedAge").value(58))
                .andExpect(jsonPath("$.recommendations", empty()))
                .andExpect(jsonPath("$.message", containsString("No product")));

        assertThat(capturedInput().candidates()).extracting(Candidate::productCode)
                .containsExactly("HD-HEALTH-001");
    }

    @Test
    void recommendationCanBeRegisteredAndIsMarkedSelected() throws Exception {
        when(model.recommend(any())).thenReturn(new ModelResult("m", List.of(
                new Pick("HD-HEALTH-001", 1, "HIGH", "Annual health cover."))));
        String body = recommend(aarav, "{\"insuranceGoals\": \"health cover\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long itemId = JsonPath.<Number>read(body, "$.recommendations[0].recommendationItemId").longValue();
        long productId = JsonPath.<Number>read(body, "$.recommendations[0].productId").longValue();

        clock.reset(); // registration start date defaults to the real "today"
        mvc.perform(post("/api/policies").header(HttpHeaders.AUTHORIZATION, "Bearer " + aarav)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": %d, \"recommendationItemId\": %d}".formatted(productId, itemId)))
                .andExpect(status().isCreated());

        assertThat(items.findById(itemId).orElseThrow().isSelectedForRegistration()).isTrue();
    }

    @Test
    void aiFailureReturns503AndSavesNothing() throws Exception {
        when(model.recommend(any())).thenThrow(new AiUnavailableException("down", null));
        long before = requests.count();

        recommend(aarav, "{\"insuranceGoals\": \"anything\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", startsWith("AI recommendations are not available right now")));

        assertThat(requests.count()).isEqualTo(before);
    }

    @Test
    void modelIsNotCalledWhenNothingIsLeftToRecommend() throws Exception {
        clock.reset();
        for (long productId : new long[]{202, 203}) {
            mvc.perform(post("/api/policies").header(HttpHeaders.AUTHORIZATION, "Bearer " + aarav)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"productId\": %d}".formatted(productId)))
                    .andExpect(status().isCreated());
        }

        recommend(aarav, "{\"insuranceGoals\": \"more cover\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.recommendations", empty()))
                .andExpect(jsonPath("$.message").value("There are no further products you are eligible for right now."));
        verifyNoInteractions(model);
    }

    @Test
    void inputsAreValidated() throws Exception {
        recommend(aarav, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Provide at least one of")));
        recommend(aarav, "{\"insuranceGoals\": \"   \"}").andExpect(status().isBadRequest());
        recommend(aarav, "{\"dateOfBirth\": \"2999-01-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Date of birth must be in the past"));
        recommend(aarav, "{\"insuranceGoals\": \"%s\"}".formatted("x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.insuranceGoals").value("Keep insurance goals under 500 characters"));
        recommend(aarav, "{\"preferredTermMonths\": 0}").andExpect(status().isBadRequest());
        recommend(aarav, "{\"preferredPolicyType\": \"PET\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(model);
    }

    @Test
    void historyShowsSavedRecommendationsNewestFirst() throws Exception {
        mvc.perform(get("/api/recommendations").header(HttpHeaders.AUTHORIZATION, "Bearer " + aarav))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value(6001))
                .andExpect(jsonPath("$[0].calculatedAge").value(36))
                .andExpect(jsonPath("$[0].recommendations[*].productCode", contains("HD-TERM-001", "HD-SAV-001")))
                .andExpect(jsonPath("$[0].recommendations[0].selectedForRegistration").value(true));

        mvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"insuranceGoals\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }
}
