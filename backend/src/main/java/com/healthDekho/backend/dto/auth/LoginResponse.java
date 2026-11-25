package com.healthDekho.backend.dto.auth;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        CustomerProfileResponse customer) {
}
