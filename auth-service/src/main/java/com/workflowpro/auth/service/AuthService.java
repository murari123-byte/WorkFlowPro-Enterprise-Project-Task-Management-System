package com.workflowpro.auth.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workflowpro.auth.dto.AuthResponse;
import com.workflowpro.auth.dto.LoginRequest;
import com.workflowpro.auth.dto.RegisterRequest;
import com.workflowpro.auth.dto.UserResponse;
import com.workflowpro.auth.entity.Role;
import com.workflowpro.auth.entity.RoleName;
import com.workflowpro.auth.entity.User;
import com.workflowpro.auth.exception.AccountDisabledException;
import com.workflowpro.auth.exception.EmailAlreadyExistsException;
import com.workflowpro.auth.exception.InvalidCredentialsException;
import com.workflowpro.auth.exception.InvalidTokenException;
import com.workflowpro.common.exception.ResourceNotFoundException;
import com.workflowpro.auth.repository.RoleRepository;
import com.workflowpro.auth.repository.UserRepository;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /** Creates an EMPLOYEE account and logs the new user in. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        Role employeeRole = roleRepository.findByName(RoleName.EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("Role EMPLOYEE missing - check Flyway migration V2"));

        User user = new User(email, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim());
        user.addRole(employeeRole);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two registrations with the same email at the same moment: the unique constraint wins
            throw new EmailAlreadyExistsException();
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Same error for "unknown email" and "wrong password", so attackers can't discover accounts
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }
        return issueTokens(user);
    }

    /**
     * Exchanges a refresh token for a new access + refresh token pair.
     * noRollbackFor: when token reuse is detected, the "revoke all sessions" update must be saved
     * even though we then throw an error.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokenService.consume(refreshToken);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshToken = refreshTokenService.create(user);
        return new AuthResponse(accessToken, refreshToken, TOKEN_TYPE, jwtService.accessTokenTtlSeconds(),
                UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
