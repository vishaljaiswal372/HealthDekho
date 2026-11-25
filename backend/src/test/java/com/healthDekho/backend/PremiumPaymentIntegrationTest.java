package com.healthDekho.backend;

import com.healthDekho.backend.entity.enums.PaymentStatus;
import com.healthDekho.backend.entity.enums.PolicyStatus;
import com.healthDekho.backend.repository.CustomerPolicyRepository;
import com.healthDekho.backend.repository.PremiumInstallmentRepository;
import com.healthDekho.backend.repository.PremiumPaymentRepository;
import com.healthDekho.backend.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.healthDekho.backend.support.TestClockConfig;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR-4 against the dev database, with a pinned business date so grace periods are deterministic.
 * Seed policy HDP-2026-0001 (Aarav): #1 (4001) paid, #2 (4002) 500 of 1250 paid, grace ends 2027-01-20,
 * #3 (4003) unpaid, grace ends 2027-02-20. Each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestClockConfig.class)
class PremiumPaymentIntegrationTest {

    private static final String SEED_PASSWORD = "Dekho@Dev2026";
    private static final String AARAV_POLICY = "HDP-2026-0001";
    private static final LocalDate ON_TIME = LocalDate.of(2026, 12, 1);
    private static final LocalDate LATE = LocalDate.of(2027, 2, 25); // after the grace periods of #2 and #3

    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @Autowired PremiumInstallmentRepository installments;
    @Autowired PremiumPaymentRepository payments;
    @Autowired CustomerPolicyRepository policies;

    private String aarav;

    @BeforeEach
    void login() throws Exception {
        clock.reset(); // tokens are issued at real time so they stay valid
        aarav = tokenFor("aarav.sharma@example.com");
        clock.setDate(ON_TIME);
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

    private ResultActions getAs(String token, String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions pay(String token, String policy, String json) throws Exception {
        return mvc.perform(post("/api/policies/{p}/payments", policy)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // ---------------------------------------------------------------- search & schedule

    @Test
    void policySearchShowsRecalculatedTotals() throws Exception {
        getAs(aarav, "/api/policies/" + AARAV_POLICY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Aarav Sharma"))
                .andExpect(jsonPath("$.policyName").value("HealthDekho Secure Term"))
                .andExpect(jsonPath("$.termMonths").value(240))
                .andExpect(jsonPath("$.startDate").value("2026-11-20"))
                .andExpect(jsonPath("$.totalPremium").value(300000.00))
                .andExpect(jsonPath("$.totalPaid").value(1750.00))
                .andExpect(jsonPath("$.outstandingAmount").value(298250.00))
                .andExpect(jsonPath("$.installmentsPaid").value(1))
                .andExpect(jsonPath("$.installmentsTotal").value(240));
    }

    @Test
    void otherCustomersPoliciesAreNotFound() throws Exception {
        getAs(aarav, "/api/policies/HDP-2026-0002")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No policy found with that policy number."));
        getAs(aarav, "/api/policies/HDP-9999-0001").andExpect(status().isNotFound());
        pay(aarav, "HDP-2026-0002", "{\"installmentIds\":[4004],\"amountPaid\":1,\"paymentMethod\":\"UPI\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void scheduleShowsStatusesAsOfToday() throws Exception {
        getAs(aarav, "/api/policies/" + AARAV_POLICY + "/installments")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(240)))
                .andExpect(jsonPath("$[0].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$[0].payable").value(false))
                .andExpect(jsonPath("$[0].period").value("2026-11"))
                .andExpect(jsonPath("$[1].paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$[1].outstandingAmount").value(750.00))
                .andExpect(jsonPath("$[1].graceEndDate").value("2027-01-20"))
                .andExpect(jsonPath("$[1].payable").value(true))
                .andExpect(jsonPath("$[2].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$[2].penaltyAmount").value(0));

        clock.setDate(LATE);
        getAs(aarav, "/api/policies/" + AARAV_POLICY + "/installments")
                .andExpect(jsonPath("$[1].paymentStatus").value("OVERDUE"))
                .andExpect(jsonPath("$[1].penaltyAmount").value(75.00))
                .andExpect(jsonPath("$[1].outstandingAmount").value(825.00))
                .andExpect(jsonPath("$[2].paymentStatus").value("OVERDUE"))
                .andExpect(jsonPath("$[2].penaltyAmount").value(125.00))
                .andExpect(jsonPath("$[3].paymentStatus").value("PENDING")); // #4 grace ends 2027-03-20
    }

    // ---------------------------------------------------------------- payments

    @Test
    void payingMultipleInstallmentsWithPartialLastOne() throws Exception {
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[4003,4002],\"amountPaid\":1500.00,\"paymentMethod\":\"UPI\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentDate").value(ON_TIME.toString()))
                .andExpect(jsonPath("$.penaltyApplied").value(0))
                .andExpect(jsonPath("$.receipts", hasSize(2)))
                // oldest first: #2 gets its remaining 750, #3 gets the other 750
                .andExpect(jsonPath("$.receipts[0].installmentNo").value(2))
                .andExpect(jsonPath("$.receipts[0].amountApplied").value(750.00))
                .andExpect(jsonPath("$.receipts[0].installmentStatus").value("PAID"))
                .andExpect(jsonPath("$.receipts[0].transactionReference").value("TXN-HD-000004"))
                .andExpect(jsonPath("$.receipts[1].installmentNo").value(3))
                .andExpect(jsonPath("$.receipts[1].amountApplied").value(750.00))
                .andExpect(jsonPath("$.receipts[1].installmentOutstanding").value(500.00))
                .andExpect(jsonPath("$.receipts[1].installmentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.receipts[1].transactionReference").value("TXN-HD-000005"))
                .andExpect(jsonPath("$.policyOutstandingAmount").value(296750.00))
                .andExpect(jsonPath("$.policyStatus").value("ACTIVE"));

        var stored = installments.findById(4003L).orElseThrow();
        assertThat(stored.getPaidAmount()).isEqualByComparingTo("750.00");
        assertThat(stored.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);

        // History is in payment-date order; seed payment 000002 is dated 2026-12-20, after today's 2026-12-01.
        getAs(aarav, "/api/policies/" + AARAV_POLICY + "/payments")
                .andExpect(jsonPath("$[*].transactionReference",
                        contains("TXN-HD-000001", "TXN-HD-000004", "TXN-HD-000005", "TXN-HD-000002")))
                .andExpect(jsonPath("$[1].paymentMethod").value("UPI"))
                .andExpect(jsonPath("$[1].paymentDate").value(ON_TIME.toString()));
    }

    @Test
    void latePaymentAppliesTenPercentPenaltyOnce() throws Exception {
        clock.setDate(LATE);
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[4002],\"amountPaid\":400.00,\"paymentMethod\":\"CARD\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.penaltyApplied").value(75.00))
                .andExpect(jsonPath("$.receipts[0].penaltyAmount").value(75.00))
                .andExpect(jsonPath("$.receipts[0].installmentOutstanding").value(425.00))
                .andExpect(jsonPath("$.receipts[0].installmentStatus").value("OVERDUE"));

        // second late payment: penalty already applied, not charged again
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[4002],\"amountPaid\":425.00,\"paymentMethod\":\"CARD\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.penaltyApplied").value(0))
                .andExpect(jsonPath("$.receipts[0].installmentStatus").value("PAID"));

        var installment = installments.findById(4002L).orElseThrow();
        assertThat(installment.getPenaltyAmount()).isEqualByComparingTo("75.00");
        assertThat(installment.getPaidAmount()).isEqualByComparingTo("1325.00");
    }

    @Test
    void amountCannotExceedOutstanding() throws Exception {
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[4002],\"amountPaid\":750.01,\"paymentMethod\":\"UPI\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("The amount exceeds the outstanding 750.00 for the selected installments."));
    }

    @Test
    void paidInstallmentsCannotBePaidAgain() throws Exception {
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[4001],\"amountPaid\":10,\"paymentMethod\":\"UPI\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Installment #1 is already paid."));
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[4003,4003],\"amountPaid\":10,\"paymentMethod\":\"UPI\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Each installment can be selected only once."));
    }

    @Test
    void everySelectedInstallmentMustReceiveSomething() throws Exception {
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[4002,4003],\"amountPaid\":750.00,\"paymentMethod\":\"UPI\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", startsWith("The amount does not reach installment #3")));
        // nothing was written
        assertThat(installments.findById(4002L).orElseThrow().getPaidAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void installmentsMustBelongToThePolicy() throws Exception {
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[4004],\"amountPaid\":10,\"paymentMethod\":\"UPI\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("One or more selected installments do not belong to this policy."));
    }

    @Test
    void requestIsValidated() throws Exception {
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[],\"amountPaid\":0,\"paymentMethod\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.installmentIds").value("Select at least one installment"))
                .andExpect(jsonPath("$.fieldErrors.amountPaid").value("Amount must be greater than zero"))
                .andExpect(jsonPath("$.fieldErrors.paymentMethod").value("Select a payment method"));
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[4003],\"amountPaid\":10.555,\"paymentMethod\":\"UPI\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amountPaid").value("Amount can have at most 2 decimal places"));
        pay(aarav, AARAV_POLICY, "{\"installmentIds\":[4003],\"amountPaid\":10,\"paymentMethod\":\"BITCOIN\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void policyBecomesFullyPaidOnlyWhenEveryInstallmentIsPaid() throws Exception {
        // Rohan registers Family Health: a single yearly installment of 18000
        clock.reset();
        String rohan = tokenFor("rohan.verma@example.com");
        clock.setDate(ON_TIME);
        String policyNumber = JsonPath.read(mvc.perform(post("/api/policies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rohan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":202}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.policyNumber");
        long installmentId = JsonPath.<Number>read(getAs(rohan, "/api/policies/" + policyNumber + "/installments")
                .andReturn().getResponse().getContentAsString(), "$[0].installmentId").longValue();

        pay(rohan, policyNumber, "{\"installmentIds\":[%d],\"amountPaid\":10000,\"paymentMethod\":\"NET_BANKING\"}"
                .formatted(installmentId))
                .andExpect(jsonPath("$.policyStatus").value("ACTIVE"));
        pay(rohan, policyNumber, "{\"installmentIds\":[%d],\"amountPaid\":8000,\"paymentMethod\":\"NET_BANKING\"}"
                .formatted(installmentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyOutstandingAmount").value(0))
                .andExpect(jsonPath("$.policyStatus").value("FULLY_PAID"));

        assertThat(policies.findByPolicyNumberWithDetails(policyNumber).orElseThrow().getStatus())
                .isEqualTo(PolicyStatus.FULLY_PAID);
        getAs(rohan, "/api/policies/" + policyNumber + "/installments")
                .andExpect(jsonPath("$[0].payable").value(false));
        pay(rohan, policyNumber, "{\"installmentIds\":[%d],\"amountPaid\":1,\"paymentMethod\":\"UPI\"}"
                .formatted(installmentId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This policy is already fully paid."));
    }

    @Test
    void paymentRowsAreStoredWithServerDate() throws Exception {
        long before = payments.count();
        String reference = JsonPath.read(pay(aarav, AARAV_POLICY,
                        "{\"installmentIds\":[4003],\"amountPaid\":1250,\"paymentMethod\":\"CASH\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.receipts[0].transactionReference");
        assertThat(payments.count()).isEqualTo(before + 1);
        var latest = payments.findByInstallmentPolicyPolicyIdOrderByPaymentDateAscPaymentIdAsc(3001L).stream()
                .filter(p -> p.getTransactionReference().equals(reference))
                .findFirst().orElseThrow();
        assertThat(latest.getPaymentDate()).isEqualTo(ON_TIME);
        assertThat(latest.getAmountPaid()).isEqualByComparingTo("1250");
    }
}
