package com.healthDekho.backend.dto.auth;

import com.healthDekho.backend.entity.Customer;

import java.time.LocalDate;

public record CustomerProfileResponse(
        Long customerId,
        String fullName,
        String email,
        LocalDate dateOfBirth,
        String phone) {

    public static CustomerProfileResponse from(Customer c) {
        return new CustomerProfileResponse(c.getCustomerId(), c.getFullName(), c.getEmail(), c.getDateOfBirth(), c.getPhone());
    }
}
