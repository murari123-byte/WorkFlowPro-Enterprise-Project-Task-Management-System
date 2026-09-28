package com.workflowpro.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.workflowpro.auth.entity.User;

/** Public view of a user. Never contains the password hash. */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        List<String> roles,
        boolean enabled,
        Instant createdAt) {

    public static UserResponse from(User user) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .toList();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                roles,
                user.isEnabled(),
                user.getCreatedAt());
    }
}
