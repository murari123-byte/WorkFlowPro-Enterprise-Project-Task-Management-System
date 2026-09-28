package com.workflowpro.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.workflowpro.auth.dto.AuthResponse;
import com.workflowpro.auth.dto.LoginRequest;
import com.workflowpro.auth.dto.RegisterRequest;
import com.workflowpro.auth.entity.Role;
import com.workflowpro.auth.entity.RoleName;
import com.workflowpro.auth.entity.User;
import com.workflowpro.auth.exception.AccountDisabledException;
import com.workflowpro.auth.exception.EmailAlreadyExistsException;
import com.workflowpro.auth.exception.InvalidCredentialsException;
import com.workflowpro.auth.repository.RoleRepository;
import com.workflowpro.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;

    private AuthService authService;
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder, jwtService,
                refreshTokenService);
        employeeRole = mock(Role.class);
    }

    @Test
    void registerStoresLowercaseEmailHashedPasswordAndEmployeeRole() {
        when(employeeRole.getName()).thenReturn(RoleName.EMPLOYEE);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(employeeRole));
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed");
        when(jwtService.createAccessToken(any())).thenReturn("access");
        when(refreshTokenService.create(any())).thenReturn("refresh");

        AuthResponse response = authService.register(
                new RegisterRequest("  Jane@Example.COM ", "Secret123!", "Jane", "Doe"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getValue().getRoles()).containsExactly(employeeRole);
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.user().roles()).containsExactly("EMPLOYEE");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("jane@example.com", "Secret123!", "Jane", "Doe")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginWithWrongPasswordFails() {
        User user = new User("jane@example.com", "hashed", "Jane", "Doe");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithUnknownEmailFailsWithSameError() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginToDisabledAccountFails() {
        User user = new User("jane@example.com", "hashed", "Jane", "Doe");
        user.setEnabled(false);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123!", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "Secret123!")))
                .isInstanceOf(AccountDisabledException.class);
        verify(jwtService, never()).createAccessToken(any());
    }
}
