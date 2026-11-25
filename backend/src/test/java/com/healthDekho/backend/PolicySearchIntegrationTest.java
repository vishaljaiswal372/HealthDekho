package com.healthDekho.backend;

import com.healthDekho.backend.support.MutableClock;
import com.healthDekho.backend.support.TestClockConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FR-5 against the dev database with a pinned business date. Each test rolls back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestClockConfig.class)
class PolicySearchIntegrationTest {

    private static final String SEED_PASSWORD = "Dekho@Dev2026";
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 1);

    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;

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

    private ResultActions search(String... params) throws Exception {
        var request = get("/api/policy-search").header(HttpHeaders.AUTHORIZATION, "Bearer " + aarav);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    private void registerForAarav(long productId) throws Exception {
        clock.reset(); // registration start date must not be before "today"; use real today
        mvc.perform(post("/api/policies").header(HttpHeaders.AUTHORIZATION, "Bearer " + aarav)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\": %d}".formatted(productId)))
                .andExpect(status().isCreated());
        clock.setDate(TODAY);
    }

    @Test
    void byPolicyIdentifier() throws Exception {
        search("policyNumber", " hdp-2026-0002 ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.content[0].policyNumber").value("HDP-2026-0002"))
                .andExpect(jsonPath("$.content[0].customerName").value("Meera Iyer"))
                .andExpect(jsonPath("$.content[0].dateOfBirth").value("1985-09-22"))
                .andExpect(jsonPath("$.content[0].policyName").value("HealthDekho Family Health"))
                .andExpect(jsonPath("$.content[0].policyType").value("HEALTH"))
                .andExpect(jsonPath("$.content[0].startDate").value("2026-11-20"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].remainingPremium").value(0));
    }

    @Test
    void remainingPremiumIsOutstandingAsOfToday() throws Exception {
        search("policyNumber", "HDP-2026-0001")
                .andExpect(jsonPath("$.content[0].remainingPremium").value(298250.00));

        clock.setDate(LocalDate.of(2027, 2, 25)); // #2 and #3 past grace: +75 and +125 penalty
        search("policyNumber", "HDP-2026-0001")
                .andExpect(jsonPath("$.content[0].remainingPremium").value(298450.00));
    }

    @Test
    void byPolicyIdentifierAndDateOfBirth() throws Exception {
        search("policyNumber", "HDP-2026-0002", "dateOfBirth", "1985-09-22")
                .andExpect(jsonPath("$.content[*].policyNumber", contains("HDP-2026-0002")));
        search("policyNumber", "HDP-2026-0002", "dateOfBirth", "1990-04-15")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.message").value("No policies match the search criteria."));
    }

    @Test
    void byCustomerNameIsPartialAndCaseInsensitive() throws Exception {
        search("customerName", "  IYER ")
                .andExpect(jsonPath("$.content[*].customerName", contains("Meera Iyer")));
        search("customerName", "mee  ra")  // inner whitespace collapsed -> "mee ra", no match
                .andExpect(jsonPath("$.content", empty()));
        search("customerName", "Verma")     // Rohan has no policies
                .andExpect(jsonPath("$.message").value("No policies match the search criteria."));
    }

    @Test
    void likeWildcardsInNamesAreLiteral() throws Exception {
        search("customerName", "%%").andExpect(jsonPath("$.content", empty()));
        search("customerName", "__").andExpect(jsonPath("$.content", empty()));
    }

    @Test
    void nameSearchIsPaginated() throws Exception {
        registerForAarav(202);
        registerForAarav(203);

        search("customerName", "sharma", "size", "2")
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].policyNumber").value("HDP-2026-0001"));
        search("customerName", "sharma", "size", "2", "page", "1")
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        search("customerName", "sharma", "size", "100")
                .andExpect(jsonPath("$.size").value(50));
        search("customerName", "sharma", "size", "101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").value("Page size must be at most 100"));
    }

    @Test
    void criteriaAreValidated() throws Exception {
        search().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Search by policy identifier")));
        search("dateOfBirth", "1985-09-22").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Date of birth must be combined with a policy identifier")));
        search("policyNumber", "HDP-2026-0001", "customerName", "Aarav").andExpect(status().isBadRequest());
        search("customerName", "a").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerName").value("Enter at least 2 characters of the customer name"));
        search("policyNumber", "HDP-2026-0001", "dateOfBirth", "15-04-1990").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Invalid value"));
        search("policyNumber", "HDP-2026-0001", "dateOfBirth", "2999-01-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Date of birth cannot be in the future"));
    }

    @Test
    void searchRequiresLogin() throws Exception {
        mvc.perform(get("/api/policy-search").param("policyNumber", "HDP-2026-0001"))
                .andExpect(status().isUnauthorized());
    }
}
