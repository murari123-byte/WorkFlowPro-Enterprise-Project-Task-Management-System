package com.workflowpro.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.workflowpro.auth.config.JwtProperties;
import com.workflowpro.auth.entity.RefreshToken;
import com.workflowpro.auth.entity.User;
import com.workflowpro.auth.exception.InvalidTokenException;
import com.workflowpro.auth.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private final User user = new User("jane@example.com", "hashed", "Jane", "Doe");

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("x".repeat(32), "test", Duration.ofMinutes(15),
                Duration.ofDays(7));
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createStoresOnlyTheHashWithSevenDayExpiry() {
        String raw = service.create(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(raw).hasSize(64);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw));
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void consumeValidTokenRevokesItAndReturnsUser() {
        RefreshToken token = new RefreshToken(user, RefreshTokenService.hash("raw"), NOW.plusSeconds(60));
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));

        assertThat(service.consume("raw")).isSameAs(user);
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void consumeExpiredTokenFails() {
        RefreshToken token = new RefreshToken(user, RefreshTokenService.hash("raw"), NOW.minusSeconds(1));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consume("raw")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void consumeUnknownTokenFails() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("nope")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void reusingRevokedTokenRevokesAllSessionsOfUser() {
        RefreshToken token = new RefreshToken(user, RefreshTokenService.hash("raw"), NOW.plusSeconds(60));
        token.revoke(NOW.minusSeconds(5));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consume("raw")).isInstanceOf(InvalidTokenException.class);
        verify(repository).revokeAllActiveForUser(user.getId(), NOW);
    }
}
