package com.workflowpro.common.security;

import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller, as proven by a verified access token. Built only from the token's claims,
 * never from request bodies or parameters.
 */
public record AuthenticatedUser(UUID id, String email, List<String> roles) {

    public static final String ADMIN = "ADMIN";
    public static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    public static final String TEAM_LEAD = "TEAM_LEAD";
    public static final String EMPLOYEE = "EMPLOYEE";

    public static AuthenticatedUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new AuthenticatedUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"),
                roles == null ? List.of() : List.copyOf(roles));
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(ADMIN);
    }
}
