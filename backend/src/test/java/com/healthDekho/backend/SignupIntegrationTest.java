package com.healthDekho.backend;

import com.healthDekho.backend.repository.CustomerRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Sign up, then log in with the new account. Each test rolls back, so no accounts are left behind. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SignupIntegrationTest {

    private static final String PASSWORD = "Secure123";

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired PasswordEncoder passwordEncoder;

    private String body(String fullName, String email, String password, String dob, String phone) {
        return """
                {"fullName": %s, "email": %s, "password": %s, "dateOfBirth": %s, "phone": %s}
                """.formatted(q(fullName), q(email), q(password), q(dob), q(phone));
    }

    private static String q(String v) {
        return v == null ? "null" : "\"" + v + "\"";
    }

    private ResultActions signup(String json) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    @Test
    void signupThenLogin() throws Exception {
        signup(body("  Priya   Nair ", "Priya.Nair@Example.com", PASSWORD, "1995-06-15", "9876500001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId", notNullValue()))
                .andExpect(jsonPath("$.fullName").value("Priya Nair"))
                .andExpect(jsonPath("$.email").value("priya.nair@example.com"))
                .andExpect(jsonPath("$.dateOfBirth").value("1995-06-15"))
                .andExpect(jsonPath("$.phone").value("9876500001"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // stored as a BCrypt hash, never as text
        var saved = customers.findByEmailIgnoreCase("priya.nair@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2a$").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();

        // the new account can log in (email in any case) and use the API
        String token = JsonPath.read(login("PRIYA.NAIR@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.fullName").value("Priya Nair"))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/dashboard/policies").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
        mvc.perform(get("/api/dashboard/available-products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(3)));

        login("priya.nair@example.com", "Wrong12345").andExpect(status().isUnauthorized());
    }

    @Test
    void phoneIsOptional() throws Exception {
        signup(body("Kabir Shah", "kabir@example.com", PASSWORD, "1990-01-01", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void emailMustBeUniqueIgnoringCase() throws Exception {
        signup(body("Another Aarav", "AARAV.SHARMA@example.com", PASSWORD, "1990-01-01", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists. Please log in instead."));
    }

    @Test
    void mustBeAnAdult() throws Exception {
        String seventeen = LocalDate.now().minusYears(17).toString();
        signup(body("Young Person", "young@example.com", PASSWORD, seventeen, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("You must be at least 18 years old to create an account"));
        assertThat(customers.existsByEmailIgnoreCase("young@example.com")).isFalse();
    }

    @Test
    void inputIsValidated() throws Exception {
        signup("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").value("Full name is required"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email is required"))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is required"))
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Date of birth is required"));

        signup(body("Bad Input", "not-an-email", "short", "2999-01-01", "12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Enter a valid email address"))
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Date of birth must be in the past"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("Enter a 10-digit mobile number"));

        signup(body("No Digits", "nodigits@example.com", "lettersonly", "1990-01-01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Password must contain at least one letter and one number"));

        signup(body("R2D2 <script>", "robot@example.com", PASSWORD, "1990-01-01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").value("Full name can contain only letters, spaces, . ' and -"));
    }

    @Test
    void signupNeedsNoTokenButOtherEndpointsStillDo() throws Exception {
        signup(body("Open Door", "open@example.com", PASSWORD, "1985-03-03", null)).andExpect(status().isCreated());
        mvc.perform(get("/api/dashboard/policies")).andExpect(status().isUnauthorized());
    }
}
