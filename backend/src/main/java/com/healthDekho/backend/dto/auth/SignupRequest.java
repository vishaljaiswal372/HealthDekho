package com.healthDekho.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** New customer account. The password is hashed with BCrypt and never stored or returned as text. */
public record SignupRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 150, message = "Full name must be 2 to 150 characters")
        @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Full name can contain only letters, spaces, . ' and -")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email is too long")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one number")
        String password,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Pattern(regexp = "^[0-9]{10}$", message = "Enter a 10-digit mobile number")
        String phone) {

    /** Keep the password out of logs and debugger output. */
    @Override
    public String toString() {
        return "SignupRequest[fullName=" + fullName + ", email=" + email + ", password=***, dateOfBirth="
                + dateOfBirth + ", phone=" + phone + "]";
    }
}
