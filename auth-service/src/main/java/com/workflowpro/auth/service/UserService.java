package com.workflowpro.auth.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workflowpro.auth.dto.ChangePasswordRequest;
import com.workflowpro.auth.dto.UpdateProfileRequest;
import com.workflowpro.auth.dto.UserResponse;
import com.workflowpro.auth.entity.Role;
import com.workflowpro.auth.entity.RoleName;
import com.workflowpro.auth.entity.User;
import com.workflowpro.auth.repository.RoleRepository;
import com.workflowpro.auth.repository.UserRepository;
import com.workflowpro.auth.repository.UserSpecifications;
import com.workflowpro.common.exception.BadRequestException;
import com.workflowpro.common.exception.BusinessRuleException;
import com.workflowpro.common.exception.ResourceNotFoundException;

/**
 * Profile (for the current user) and user administration (for ADMIN).
 * Role checks for "who may call this" are on the controller (@PreAuthorize);
 * rules about the data itself (e.g. "an admin cannot disable themselves") live here.
 */
@Service
public class UserService {

    /** Upper limit for batch lookups from other services. */
    public static final int MAX_BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return UserResponse.from(findUser(id));
    }

    /** Users by id, in no particular order. Unknown ids are skipped. */
    @Transactional(readOnly = true)
    public List<UserResponse> getUsers(Collection<UUID> ids) {
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new BadRequestException("At most " + MAX_BATCH_SIZE + " ids per request");
        }
        return userRepository.findAllById(ids).stream().map(UserResponse::from).toList();
    }

    /**
     * @param includeDisabled only admins see disabled accounts
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> search(String search, RoleName role, boolean includeDisabled, Pageable pageable) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.matchesText(search),
                UserSpecifications.hasRole(role),
                UserSpecifications.enabledOnly(!includeDisabled));
        return userRepository.findAll(spec, pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.updateName(request.firstName().trim(), request.lastName().trim());
        return UserResponse.from(user);
    }

    /** Also signs the user out of every other session (all refresh tokens revoked). */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current one");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(user);
    }

    /** New roles are in the user's NEXT access token (after refresh or login). */
    @Transactional
    public UserResponse updateRoles(UUID adminId, UUID userId, Set<RoleName> roleNames) {
        User user = findUser(userId);
        if (adminId.equals(userId) && !roleNames.contains(RoleName.ADMIN)) {
            throw new BusinessRuleException("You cannot remove your own ADMIN role");
        }
        List<Role> roles = roleRepository.findByNameIn(roleNames);
        user.replaceRoles(new HashSet<>(roles));
        return UserResponse.from(user);
    }

    /** Disabling blocks login and refresh immediately; an existing access token lives until it expires. */
    @Transactional
    public UserResponse updateStatus(UUID adminId, UUID userId, boolean enabled) {
        if (adminId.equals(userId) && !enabled) {
            throw new BusinessRuleException("You cannot disable your own account");
        }
        User user = findUser(userId);
        user.setEnabled(enabled);
        if (!enabled) {
            refreshTokenService.revokeAllForUser(user);
        }
        return UserResponse.from(user);
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
