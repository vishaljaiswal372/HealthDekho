package com.healthDekho.backend.controller;

import com.healthDekho.backend.dto.auth.CustomerProfileResponse;
import com.healthDekho.backend.dto.auth.LoginRequest;
import com.healthDekho.backend.dto.auth.LoginResponse;
import com.healthDekho.backend.dto.auth.SignupRequest;
import com.healthDekho.backend.security.CurrentCustomer;
import com.healthDekho.backend.service.AuthService;
import com.healthDekho.backend.service.SignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SignupService signupService;
    private final CurrentCustomer currentCustomer;

    /** Creates a customer account (no login needed). The customer then logs in with POST /api/auth/login. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerProfileResponse signup(@Valid @RequestBody SignupRequest request) {
        return signupService.signup(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CustomerProfileResponse me() {
        return authService.profile(currentCustomer.id());
    }
}
