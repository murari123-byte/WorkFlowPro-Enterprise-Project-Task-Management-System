package com.workflowpro.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.workflowpro.auth.config.TokenProperties;
import com.workflowpro.common.security.JwtProperties;
import com.workflowpro.auth.entity.User;

/**
 * Creates signed access tokens (JWT, HS256).
 *
 * Claims: iss, sub (user id), email, roles, iat, exp.
 * Roles are taken from the database at the moment the token is created.
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final TokenProperties tokenProperties;
    private final Clock clock;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, TokenProperties tokenProperties,
                      Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.tokenProperties = tokenProperties;
        this.clock = clock;
    }

    public String createAccessToken(User user) {
        Instant now = clock.instant();
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(tokenProperties.accessTokenTtl()))
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return tokenProperties.accessTokenTtl().toSeconds();
    }
}
