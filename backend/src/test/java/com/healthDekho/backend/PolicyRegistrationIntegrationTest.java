package com.healthDekho.backend;

import com.healthDekho.backend.entity.InsuranceProduct;
import com.healthDekho.backend.entity.PremiumInstallment;
import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.entity.enums.PremiumFrequency;
import com.healthDekho.backend.repository.AiRecommendationItemRepository;
import com.healthDekho.backend.repository.CustomerPolicyRepository;
import com.healthDekho.backend.repository.InsuranceProductRepository;
import com.healthDekho.backend.repository.PremiumInstallmentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FR-3 against the dev database. Each test rolls back, so seed data is never changed. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PolicyRegistrationIntegrationTest {

    private static final String SEED_PASSWORD = "Dekho@Dev2026";
    private static final long SECURE_TERM = 201, FAMILY_HEALTH = 202, SMART_SAVINGS = 203;

    @Autowired MockMvc mvc;
    @Autowired CustomerPolicyRepository policies;
    @Autowired PremiumInstallmentRepository installments;
    @Autowired InsuranceProductRepository products;
    @Autowired AiRecommendationItemRepository recommendationItems;

    private String aarav;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void login() throws Exception {
        aarav = tokenFor("aarav.sharma@example.com");
    }

    private String tokenFor(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, SEED_PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions register(String token, String json) throws Exception {
        return mvc.perform(post("/api/policies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions preview(String token, long productId) throws Exception {
        return mvc.perform(get("/api/policies/registration-preview")
                .param("productId", String.valueOf(productId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void previewPrePopulatesTheForm() throws Exception {
        preview(aarav, FAMILY_HEALTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Aarav Sharma"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-04-15"))
                .andExpect(jsonPath("$.email").value("aarav.sharma@example.com"))
                .andExpect(jsonPath("$.phone").value("9876501001"))
                .andExpect(jsonPath("$.policyType").value("HEALTH"))
                .andExpect(jsonPath("$.policyName").value("HealthDekho Family Health"))
                .andExpect(jsonPath("$.termMonths").value(12))
                .andExpect(jsonPath("$.startDate").value(today.toString()))
                .andExpect(jsonPath("$.maturityDate").value(today.plusMonths(12).minusDays(1).toString()))
                .andExpect(jsonPath("$.premiumFrequency").value("YEARLY"))
                .andExpect(jsonPath("$.numberOfInstallments").value(1))
                .andExpect(jsonPath("$.totalPremiumPayable").value(18000.00))
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.ineligibilityReason").doesNotExist());
    }

    @Test
    void registrationCreatesPolicyAndFullSchedule() throws Exception {
        String body = register(aarav, "{\"productId\": %d}".formatted(SMART_SAVINGS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyNumber", matchesPattern("HDP-" + today.getYear() + "-\\d{4}")))
                .andExpect(jsonPath("$.policyName").value("HealthDekho Smart Savings"))
                .andExpect(jsonPath("$.startDate").value(today.toString()))
                .andExpect(jsonPath("$.maturityDate").value(today.plusMonths(120).minusDays(1).toString()))
                .andExpect(jsonPath("$.totalPremium").value(300000.00))
                .andExpect(jsonPath("$.numberOfInstallments").value(40))
                .andExpect(jsonPath("$.firstDueDate").value(today.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.message", startsWith("Policy HDP-")))
                .andReturn().getResponse().getContentAsString();

        String policyNumber = JsonPath.read(body, "$.policyNumber");
        var policy = policies.findByPolicyNumberWithDetails(policyNumber).orElseThrow();
        assertThat(policy.getCustomer().getCustomerId()).isEqualTo(1001L);

        List<PremiumInstallment> schedule = installments.findByPolicyPolicyIdOrderByInstallmentNoAsc(policy.getPolicyId());
        assertThat(schedule).hasSize(40);
        assertThat(schedule.get(1).getDueDate()).isEqualTo(today.plusMonths(3));
        assertThat(schedule.get(1).getGraceEndDate()).isEqualTo(today.plusMonths(4));
        assertThat(schedule).allSatisfy(i -> {
            assertThat(i.getScheduledAmount()).isEqualByComparingTo("7500.00");
            assertThat(i.getPaidAmount()).isEqualByComparingTo("0");
            assertThat(i.getPaymentStatus().name()).isEqualTo("PENDING");
        });

        // FR-1 views now reflect the new policy
        mvc.perform(get("/api/dashboard/policies").header(HttpHeaders.AUTHORIZATION, "Bearer " + aarav))
                .andExpect(jsonPath("$[*].policyNumber", hasItems("HDP-2026-0001", policyNumber)));
        mvc.perform(get("/api/dashboard/available-products").header(HttpHeaders.AUTHORIZATION, "Bearer " + aarav))
                .andExpect(jsonPath("$[*].productCode", contains("HD-HEALTH-001")));
    }

    @Test
    void policyNumbersAreSequential() throws Exception {
        String first = JsonPath.read(register(aarav, "{\"productId\": %d}".formatted(FAMILY_HEALTH))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.policyNumber");
        String second = JsonPath.read(register(aarav, "{\"productId\": %d}".formatted(SMART_SAVINGS))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.policyNumber");

        int a = Integer.parseInt(first.substring(first.lastIndexOf('-') + 1));
        int b = Integer.parseInt(second.substring(second.lastIndexOf('-') + 1));
        assertThat(b).isEqualTo(a + 1);
    }

    @Test
    void duplicateRegistrationIsRefused() throws Exception {
        register(aarav, "{\"productId\": %d}".formatted(SECURE_TERM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You already have a policy for HealthDekho Secure Term."));
        preview(aarav, SECURE_TERM).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void entryAgeIsEnforced() throws Exception {
        var seniorPlan = new InsuranceProduct();
        seniorPlan.setProductCode("HD-SENIOR-TEST");
        seniorPlan.setPolicyName("Senior Test Plan");
        seniorPlan.setPolicyType(PolicyType.HEALTH);
        seniorPlan.setTermMonths(12);
        seniorPlan.setPremiumFrequency(PremiumFrequency.YEARLY);
        seniorPlan.setPremiumAmount(new BigDecimal("5000.00"));
        seniorPlan.setSumInsured(new BigDecimal("200000.00"));
        seniorPlan.setMinEntryAge(60);
        seniorPlan.setMaxEntryAge(80);
        long id = products.saveAndFlush(seniorPlan).getProductId();

        preview(aarav, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.ineligibilityReason", containsString("ages 60 to 80")));
        register(aarav, "{\"productId\": %d}".formatted(id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("ages 60 to 80")));
    }

    @Test
    void startDateCannotBeInThePast() throws Exception {
        register(aarav, "{\"productId\": %d, \"startDate\": \"%s\"}".formatted(FAMILY_HEALTH, today.minusDays(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Policy start date cannot be in the past."));
    }

    @Test
    void futureStartDateDrivesMaturityAndSchedule() throws Exception {
        LocalDate start = today.plusDays(10);
        register(aarav, "{\"productId\": %d, \"startDate\": \"%s\"}".formatted(FAMILY_HEALTH, start))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value(start.toString()))
                .andExpect(jsonPath("$.maturityDate").value(start.plusMonths(12).minusDays(1).toString()))
                .andExpect(jsonPath("$.firstDueDate").value(start.toString()));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        register(aarav, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.productId").value("Select an insurance product"));
        register(aarav, "{\"productId\": 99999}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Insurance product not found or no longer available."));
        mvc.perform(get("/api/policies/registration-preview").param("productId", "abc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aarav))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.productId").value("Invalid value"));
        mvc.perform(get("/api/policies/registration-preview").param("productId", "-5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aarav))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.productId").exists());
        mvc.perform(get("/api/policies/registration-preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aarav))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON).content("{\"productId\": 202}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registeringFromARecommendationMarksItSelected() throws Exception {
        // Seed item 7002: Aarav's request 6001 recommended Smart Savings (203), not yet selected.
        register(aarav, "{\"productId\": %d, \"recommendationItemId\": 7002}".formatted(SMART_SAVINGS))
                .andExpect(status().isCreated());
        assertThat(recommendationItems.findById(7002L).orElseThrow().isSelectedForRegistration()).isTrue();
    }

    @Test
    void recommendationMustBelongToCustomerAndMatchProduct() throws Exception {
        // 7003 belongs to Rohan
        register(aarav, "{\"productId\": %d, \"recommendationItemId\": 7003}".formatted(FAMILY_HEALTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Recommendation not found."));
        // 7002 is Aarav's but for Smart Savings, not Family Health
        register(aarav, "{\"productId\": %d, \"recommendationItemId\": 7002}".formatted(FAMILY_HEALTH))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("The recommendation does not match the selected product."));
        assertThat(policies.existsByCustomerCustomerIdAndProductProductId(1001L, FAMILY_HEALTH)).isFalse();
    }
}
