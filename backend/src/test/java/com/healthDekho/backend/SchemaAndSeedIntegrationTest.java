package com.healthDekho.backend;

import com.healthDekho.backend.entity.CustomerPolicy;
import com.healthDekho.backend.entity.InsuranceProduct;
import com.healthDekho.backend.entity.PremiumInstallment;
import com.healthDekho.backend.entity.enums.PaymentStatus;
import com.healthDekho.backend.entity.enums.PolicyType;
import com.healthDekho.backend.entity.enums.PremiumFrequency;
import com.healthDekho.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real dev database: Flyway migrations + spec 5.5 seed data,
 * Hibernate schema validation, and the repository queries. Every test rolls back.
 */
@SpringBootTest
@Transactional
class SchemaAndSeedIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerRepository customers;
    @Autowired InsuranceProductRepository products;
    @Autowired CustomerPolicyRepository policies;
    @Autowired PremiumInstallmentRepository installments;
    @Autowired PremiumPaymentRepository payments;
    @Autowired AiRecommendationRequestRepository recommendationRequests;

    @Test
    void flywayAppliedSchemaAndSeed() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).containsExactly("1", "100");
    }

    @Test
    void seedRowCountsMatchSpec() {
        assertThat(customers.count()).isEqualTo(3);
        assertThat(products.count()).isEqualTo(3);
        assertThat(policies.count()).isEqualTo(2);
        assertThat(installments.count()).isEqualTo(241); // 240 for HDP-2026-0001 + 1 for HDP-2026-0002
        assertThat(payments.count()).isEqualTo(3);
        assertThat(recommendationRequests.count()).isEqualTo(2);
    }

    @Test
    void seededPasswordHashesAreRealBcrypt() {
        var encoder = new BCryptPasswordEncoder();
        customers.findAll().forEach(c ->
                assertThat(c.getPasswordHash()).startsWith("$2a$10$").hasSize(60));
        // hashes must at least be well-formed enough for the encoder to evaluate
        assertThat(encoder.matches("wrong-password", customers.findAll().getFirst().getPasswordHash())).isFalse();
    }

    @Test
    void installmentScheduleAddsUpToTotalPremium() {
        CustomerPolicy policy = policies.findByPolicyNumberWithDetails("HDP-2026-0001").orElseThrow();
        List<PremiumInstallment> schedule = installments.findByPolicyPolicyIdOrderByInstallmentNoAsc(policy.getPolicyId());

        assertThat(schedule).hasSize(PremiumFrequency.MONTHLY.installmentsFor(policy.getTermMonths()));
        BigDecimal scheduledTotal = schedule.stream()
                .map(PremiumInstallment::getScheduledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(scheduledTotal).isEqualByComparingTo(policy.getTotalPremium());

        assertThat(schedule.get(1).getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(schedule.get(1).outstandingAmount()).isEqualByComparingTo("750.00");
        assertThat(schedule.getLast().getDueDate()).isEqualTo(LocalDate.of(2046, 10, 20));
        // 300000 total - 1250 - 500 already paid
        assertThat(installments.sumOutstandingByPolicyId(policy.getPolicyId())).isEqualByComparingTo("298250.00");
    }

    @Test
    void availableProductsExcludeAlreadyRegistered() {
        assertThat(products.findAvailableForCustomer(1001L))
                .extracting(InsuranceProduct::getProductCode)
                .containsExactlyInAnyOrder("HD-HEALTH-001", "HD-SAV-001");
        assertThat(products.findAvailableForCustomer(1003L)).hasSize(3);
        assertThat(policies.existsByCustomerCustomerIdAndProductProductId(1002L, 202L)).isTrue();
    }

    @Test
    void policySearchQueriesWork() {
        assertThat(policies.searchByCustomerName("%sharma%", PageRequest.of(0, 10)).getContent())
                .extracting(CustomerPolicy::getPolicyNumber)
                .containsExactly("HDP-2026-0001");
        assertThat(policies.findByPolicyNumberAndCustomerDob("HDP-2026-0002", LocalDate.of(1985, 9, 22))).isPresent();
        assertThat(policies.findByPolicyNumberAndCustomerDob("HDP-2026-0002", LocalDate.of(1990, 1, 1))).isEmpty();
        assertThat(policies.findMaxPolicyNumberWithPrefix("HDP-2026-")).contains("HDP-2026-0002");
    }

    @Test
    void newRowsGetIdsAfterSeededOnes() {
        var product = new InsuranceProduct();
        product.setProductCode("HD-TEST-001");
        product.setPolicyName("Test Product");
        product.setPolicyType(PolicyType.LIFE);
        product.setTermMonths(12);
        product.setPremiumFrequency(PremiumFrequency.MONTHLY);
        product.setPremiumAmount(new BigDecimal("100.00"));
        product.setSumInsured(new BigDecimal("10000.00"));
        product.setMinEntryAge(18);
        product.setMaxEntryAge(60);

        assertThat(products.saveAndFlush(product).getProductId()).isGreaterThan(203L);
    }

    @Test
    void databaseRejectsDuplicateRegistration() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into customer_policy (policy_number, customer_id, product_id, term_months, start_date, maturity_date,
                    premium_frequency, premium_amount, sum_insured, total_premium)
                values ('HDP-TEST-9999', 1001, 201, 240, current_date, current_date + 1, 'MONTHLY', 1250, 5000000, 300000)
                """)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void paymentRowsAreImmutable() {
        assertThatThrownBy(() -> jdbc.update("update premium_payment set amount_paid = 1 where payment_id = 5001"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }
}
