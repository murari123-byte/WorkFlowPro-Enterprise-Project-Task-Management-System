package com.workflowpro.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.workflowpro.auth.dto.ChangePasswordRequest;
import com.workflowpro.auth.entity.RoleName;
import com.workflowpro.auth.entity.User;
import com.workflowpro.auth.repository.RoleRepository;
import com.workflowpro.auth.repository.UserRepository;
import com.workflowpro.common.exception.BadRequestException;
import com.workflowpro.common.exception.BusinessRuleException;
import com.workflowpro.common.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;

    private UserService userService;
    private final UUID adminId = UUID.randomUUID();
    private final User user = new User("jane@example.com", "old-hash", "Jane", "Doe");

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleRepository, passwordEncoder, refreshTokenService);
    }

    @Test
    void adminCannotRemoveOwnAdminRole() {
        when(userRepository.findById(adminId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateRoles(adminId, adminId, Set.of(RoleName.EMPLOYEE)))
                .isInstanceOf(BusinessRuleException.class);
        verify(roleRepository, never()).findByNameIn(any());
    }

    @Test
    void adminCannotDisableThemselves() {
        assertThatThrownBy(() -> userService.updateStatus(adminId, adminId, false))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void disablingUserRevokesTheirSessions() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.updateStatus(adminId, userId, false);

        assertThat(user.isEnabled()).isFalse();
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void changePasswordRequiresCorrectCurrentPassword() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(userId, new ChangePasswordRequest("wrong", "NewPass123!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Current password is incorrect");
    }

    @Test
    void changePasswordStoresNewHashAndSignsOutEverywhere() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass123!", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123!", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("new-hash");

        userService.changePassword(userId, new ChangePasswordRequest("OldPass123!", "NewPass123!"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void unknownUserGives404() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
