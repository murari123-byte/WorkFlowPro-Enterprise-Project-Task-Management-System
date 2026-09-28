package com.workflowpro.common.client;

import java.util.UUID;

/** Just enough about a user to show their name in another service's response. */
public record UserSummary(UUID id, String firstName, String lastName, String email) {

    /** Shown when auth-service no longer knows the user. */
    public static UserSummary unknown(UUID id) {
        return new UserSummary(id, "Unknown", "user", null);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
