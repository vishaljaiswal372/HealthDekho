package com.healthDekho.backend.service;

import com.healthDekho.backend.config.SecurityProperties;
import com.healthDekho.backend.dto.auth.CustomerProfileResponse;
import com.healthDekho.backend.dto.auth.LoginRequest;
import com.healthDekho.backend.dto.auth.LoginResponse;
import com.healthDekho.backend.entity.Customer;
import com.healthDekho.backend.exception.ResourceNotFoundException;
import com.healthDekho.backend.repository.CustomerRepository;
import com.healthDekho.backend.security.CurrentCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ISSUER = "healthdekho";

    private final AuthenticationManager authenticationManager;
    private final CustomerRepository customerRepository;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    /** Verifies credentials (throws BadCredentialsException etc. on failure) and issues an access token. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));

        Customer customer = customerRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        Instant now = clock.instant();
        Instant expiresAt = now.plus(securityProperties.tokenValidity());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(customer.getEmail())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CurrentCustomer.CUSTOMER_ID_CLAIM, customer.getCustomerId())
                .claim("scope", "CUSTOMER")
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return new LoginResponse(token, "Bearer", expiresAt, CustomerProfileResponse.from(customer));
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse profile(long customerId) {
        return customerRepository.findById(customerId)
                .map(CustomerProfileResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }
}
