package com.healthDekho.backend.service;

import com.healthDekho.backend.dto.auth.CustomerProfileResponse;
import com.healthDekho.backend.dto.auth.SignupRequest;
import com.healthDekho.backend.entity.Customer;
import com.healthDekho.backend.entity.enums.CustomerStatus;
import com.healthDekho.backend.exception.ConflictException;
import com.healthDekho.backend.exception.FieldValidationException;
import com.healthDekho.backend.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

/** Creates customer accounts. After signing up, the customer logs in through the normal login endpoint. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    static final int MINIMUM_AGE = 18;
    static final String EMAIL_TAKEN = "An account with this email already exists. Please log in instead.";

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public CustomerProfileResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        int age = PremiumCalculator.ageOn(request.dateOfBirth(), LocalDate.now(clock));
        if (age < MINIMUM_AGE) {
            throw new FieldValidationException("dateOfBirth",
                    "You must be at least %d years old to create an account".formatted(MINIMUM_AGE));
        }
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(EMAIL_TAKEN);
        }

        Customer customer = new Customer();
        customer.setFullName(request.fullName().trim().replaceAll("\\s+", " "));
        customer.setEmail(email);
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        customer.setStatus(CustomerStatus.ACTIVE);

        try {
            customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the UNIQUE constraint decides.
            throw new ConflictException(EMAIL_TAKEN);
        }
        log.info("New customer account {} created", customer.getCustomerId());
        return CustomerProfileResponse.from(customer);
    }
}
