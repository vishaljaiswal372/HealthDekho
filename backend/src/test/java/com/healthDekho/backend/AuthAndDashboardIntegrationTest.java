package com.healthDekho.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FR-1 end to end against the dev database (seed data from spec 5.5). */
@SpringBootTest
@AutoConfigureMockMvc
class AuthAndDashboardIntegrationTest {

    /** Dev seed password, see db/seed/V100__sample_data.sql. */
    private static final String SEED_PASSWORD = "Dekho@Dev2026";

    @Autowired MockMvc mvc;

    private String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }

    private String tokenFor(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, SEED_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    @Test
    void loginReturnsTokenAndProfile() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("Aarav.Sharma@example.com", SEED_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.customer.customerId").value(1001))
                .andExpect(jsonPath("$.customer.fullName").value("Aarav Sharma"))
                .andExpect(jsonPath("$.customer.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
        for (String email : new String[]{"aarav.sharma@example.com", "nobody@example.com"}) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, "wrong-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password."));
        }
    }

    @Test
    void loginValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("not-an-email", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Enter a valid email address"))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is required"));
    }

    @Test
    void dashboardRequiresAValidToken() throws Exception {
        mvc.perform(get("/api/dashboard/policies"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.message").value("Please log in to continue."));

        String token = tokenFor("aarav.sharma@example.com");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mvc.perform(get("/api/dashboard/policies").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardShowsOnlyTheLoggedInCustomersPolicies() throws Exception {
        mvc.perform(get("/api/dashboard/policies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("aarav.sharma@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].policyNumber").value("HDP-2026-0001"))
                .andExpect(jsonPath("$[0].policyName").value("HealthDekho Secure Term"))
                .andExpect(jsonPath("$[0].policyType").value("TERM"))
                .andExpect(jsonPath("$[0].termMonths").value(240))
                .andExpect(jsonPath("$[0].startDate").value("2026-11-20"))
                .andExpect(jsonPath("$[0].maturityDate").value("2046-11-19"))
                .andExpect(jsonPath("$[0].premiumFrequency").value("MONTHLY"))
                .andExpect(jsonPath("$[0].premiumAmount").value(1250.00))
                .andExpect(jsonPath("$[0].sumInsured").value(5000000.00))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mvc.perform(get("/api/dashboard/policies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("meera.iyer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].policyNumber", contains("HDP-2026-0002")));

        mvc.perform(get("/api/dashboard/policies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("rohan.verma@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void availableProductsExcludeRegisteredOnes() throws Exception {
        mvc.perform(get("/api/dashboard/available-products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("aarav.sharma@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productCode", containsInAnyOrder("HD-HEALTH-001", "HD-SAV-001")))
                .andExpect(jsonPath("$[0].productId", notNullValue()));

        mvc.perform(get("/api/dashboard/available-products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("rohan.verma@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void meReturnsProfileFromToken() throws Exception {
        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("meera.iyer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1002))
                .andExpect(jsonPath("$.dateOfBirth").value("1985-09-22"));
    }

    @Test
    void corsPreflightAllowsTheDevFrontend() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/dashboard/policies")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }
}
