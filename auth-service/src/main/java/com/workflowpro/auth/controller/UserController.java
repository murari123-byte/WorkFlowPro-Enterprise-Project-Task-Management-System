package com.workflowpro.auth.controller;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.workflowpro.auth.config.OpenApiConfig;
import com.workflowpro.auth.dto.ChangePasswordRequest;
import com.workflowpro.auth.dto.UpdateProfileRequest;
import com.workflowpro.auth.dto.UpdateRolesRequest;
import com.workflowpro.auth.dto.UpdateStatusRequest;
import com.workflowpro.auth.dto.UserResponse;
import com.workflowpro.auth.entity.RoleName;
import com.workflowpro.auth.service.UserService;
import com.workflowpro.common.security.AuthenticatedUser;
import com.workflowpro.common.web.PageResponse;
import com.workflowpro.common.web.Paging;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController {

    private static final Set<String> SORT_FIELDS = Set.of("email", "firstName", "lastName", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by("firstName", "lastName");

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ---------- current user (profile) ----------

    @GetMapping("/me")
    @Operation(summary = "My profile")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getUser(AuthenticatedUser.from(jwt).id());
    }

    @PutMapping("/me")
    @Operation(summary = "Update my first and last name")
    public UserResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(AuthenticatedUser.from(jwt).id(), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change my password (signs out all other sessions)")
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(AuthenticatedUser.from(jwt).id(), request);
    }

    // ---------- directory (used to pick members / assignees, and by other services) ----------

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD')")
    @Operation(summary = "Search users (ADMIN, PROJECT_MANAGER, TEAM_LEAD). Only ADMIN sees disabled users")
    public PageResponse<UserResponse> search(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(required = false) RoleName role,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String sort) {
        boolean isAdmin = AuthenticatedUser.from(jwt).isAdmin();
        return PageResponse.from(
                userService.search(search, role, isAdmin, Paging.pageable(page, size, sort, SORT_FIELDS, DEFAULT_SORT)),
                user -> user);
    }

    @GetMapping("/batch")
    @Operation(summary = "Look up several users by id (max 100). Unknown ids are skipped")
    public List<UserResponse> batch(@RequestParam List<UUID> ids) {
        return userService.getUsers(ids);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One user by id")
    public UserResponse get(@PathVariable UUID id) {
        return userService.getUser(id);
    }

    // ---------- administration (ADMIN only) ----------

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a user's roles (ADMIN). Takes effect in the user's next access token")
    public UserResponse updateRoles(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody UpdateRolesRequest request) {
        return userService.updateRoles(AuthenticatedUser.from(jwt).id(), id, request.roles());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enable or disable an account (ADMIN)")
    public UserResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        return userService.updateStatus(AuthenticatedUser.from(jwt).id(), id, request.enabled());
    }
}
