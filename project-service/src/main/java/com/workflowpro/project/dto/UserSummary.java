package com.workflowpro.project.dto;

import java.util.UUID;

/** Just enough about a user to show their name. Comes from auth-service. */
public record UserSummary(UUID id, String firstName, String lastName, String email) {

    /** Shown when auth-service no longer knows the user. */
    public static UserSummary unknown(UUID id) {
        return new UserSummary(id, "Unknown", "user", null);
    }
}
