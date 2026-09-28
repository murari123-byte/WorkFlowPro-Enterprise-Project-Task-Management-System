package com.workflowpro.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration input. There is deliberately NO role field: every new user is an EMPLOYEE.
 * Any "roles" sent by the client is ignored.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt only uses the first 72 bytes, so longer passwords are rejected
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName) {
}
