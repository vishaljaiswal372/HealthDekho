package com.healthDekho.backend.security;

import com.healthDekho.backend.entity.enums.CustomerStatus;
import com.healthDekho.backend.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authenticates customers by email against the BCrypt hash stored in the customer table. */
@Service
@RequiredArgsConstructor
public class CustomerUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        var customer = customerRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown customer"));
        return User.withUsername(customer.getEmail())
                .password(customer.getPasswordHash())
                .disabled(customer.getStatus() == CustomerStatus.INACTIVE)
                .accountLocked(customer.getStatus() == CustomerStatus.LOCKED)
                .roles("CUSTOMER")
                .build();
    }
}
