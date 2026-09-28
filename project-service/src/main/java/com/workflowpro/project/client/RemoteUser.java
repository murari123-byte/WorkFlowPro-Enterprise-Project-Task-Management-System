package com.workflowpro.project.client;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.workflowpro.project.dto.UserSummary;

/** The part of auth-service's UserResponse that project-service needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteUser(UUID id, String email, String firstName, String lastName, List<String> roles,
                         boolean enabled) {

    public boolean hasAnyRole(String... wanted) {
        for (String role : wanted) {
            if (roles != null && roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    public UserSummary toSummary() {
        return new UserSummary(id, firstName, lastName, email);
    }
}
