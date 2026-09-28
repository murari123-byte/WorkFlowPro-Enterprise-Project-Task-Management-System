package com.workflowpro.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthenticatedUserTest {

    @Test
    void buildsUserFromTokenClaims() {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256")
                .subject(id.toString()).claim("email", "a@b.com").claim("roles", List.of("ADMIN", "TEAM_LEAD"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        AuthenticatedUser user = AuthenticatedUser.from(jwt);

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.email()).isEqualTo("a@b.com");
        assertThat(user.isAdmin()).isTrue();
        assertThat(user.hasRole("TEAM_LEAD")).isTrue();
        assertThat(user.hasRole("EMPLOYEE")).isFalse();
    }

    @Test
    void tokenWithoutRolesMeansNoRoles() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThat(AuthenticatedUser.from(jwt).roles()).isEmpty();
    }
}
