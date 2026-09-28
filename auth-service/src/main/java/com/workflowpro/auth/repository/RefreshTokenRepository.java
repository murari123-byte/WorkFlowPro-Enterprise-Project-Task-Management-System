package com.workflowpro.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.workflowpro.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revokes every still-active token of a user (used when a stolen token is detected). */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
