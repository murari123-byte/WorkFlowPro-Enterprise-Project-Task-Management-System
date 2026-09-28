package com.workflowpro.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.workflowpro.auth.config.TokenProperties;
import com.workflowpro.auth.entity.RefreshToken;
import com.workflowpro.auth.entity.User;
import com.workflowpro.auth.exception.InvalidTokenException;
import com.workflowpro.auth.repository.RefreshTokenRepository;

/**
 * Refresh tokens are random strings (not JWTs). Only their SHA-256 hash is saved.
 *
 * Rotation: each refresh token can be used once. Using it revokes it and a new one is issued.
 * Reuse detection: if an already-revoked token is presented, someone may have stolen it,
 * so ALL of that user's active refresh tokens are revoked and the user must log in again.
 *
 * Not @Transactional itself: it always runs inside the caller's (AuthService) transaction.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, TokenProperties properties,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates a new refresh token and returns the raw value (shown to the client once). */
    public String create(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(user, hash(rawToken), expiresAt));
        return rawToken;
    }

    /** Validates and revokes the token, returning its user so new tokens can be issued. */
    public User consume(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidTokenException::new);

        if (token.isRevoked()) {
            User user = token.getUser();
            log.warn("Revoked refresh token reused for user {} - revoking all their sessions", user.getId());
            refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);
            throw new InvalidTokenException();
        }
        if (token.isExpired(now) || !token.getUser().isEnabled()) {
            throw new InvalidTokenException();
        }

        token.revoke(now);
        return token.getUser();
    }

    /** Logout: revokes the token if it exists and is active. Unknown tokens are ignored. */
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
