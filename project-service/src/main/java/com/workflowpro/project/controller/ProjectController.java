package com.workflowpro.project.controller;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.workflowpro.common.security.AuthenticatedUser;
import com.workflowpro.common.web.PageResponse;
import com.workflowpro.common.web.Paging;
import com.workflowpro.project.dto.AccessibleProjectsResponse;
import com.workflowpro.project.dto.ProjectMembershipResponse;
import com.workflowpro.project.dto.ProjectRequest;
import com.workflowpro.project.dto.ProjectResponse;
import com.workflowpro.project.dto.ProjectStatsResponse;
import com.workflowpro.project.dto.ProjectSummaryResponse;
import com.workflowpro.project.dto.StatusChangeRequest;
import com.workflowpro.project.dto.UserIdRequest;
import com.workflowpro.project.entity.ProjectStatus;
import com.workflowpro.project.service.ProjectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects")
public class ProjectController {

    private static final Set<String> SORT_FIELDS = Set.of("name", "status", "startDate", "endDate", "createdAt",
            "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Create a project (ADMIN, PROJECT_MANAGER). Starts in PLANNING")
    public ProjectResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectRequest request) {
        return projectService.create(AuthenticatedUser.from(jwt), request);
    }

    @GetMapping
    @Operation(summary = "Projects I can see (ADMIN: all), with search, filters, sorting and paging")
    public PageResponse<ProjectSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(required = false) String search,
                                                     @RequestParam(required = false) ProjectStatus status,
                                                     @RequestParam(required = false) UUID managerId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestParam(required = false) String sort) {
        return PageResponse.from(projectService.list(AuthenticatedUser.from(jwt), search, status, managerId,
                Paging.pageable(page, size, sort, SORT_FIELDS, DEFAULT_SORT)), project -> project);
    }

    @GetMapping("/stats")
    @Operation(summary = "Project counts for the dashboard (only projects I can see)")
    public ProjectStatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        return projectService.stats(AuthenticatedUser.from(jwt));
    }

    @GetMapping("/accessible")
    @Operation(summary = "Ids of the projects I can see (used by task-service)")
    public AccessibleProjectsResponse accessible(@AuthenticationPrincipal Jwt jwt) {
        return projectService.accessibleProjects(AuthenticatedUser.from(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Project details with members")
    public ProjectResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return projectService.get(AuthenticatedUser.from(jwt), id);
    }

    @GetMapping("/{id}/membership")
    @Operation(summary = "Status, manager and member ids of a project (used by task-service)")
    public ProjectMembershipResponse membership(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return projectService.membership(AuthenticatedUser.from(jwt), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update name, description and dates (project manager or ADMIN)")
    public ProjectResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody ProjectRequest request) {
        return projectService.update(AuthenticatedUser.from(jwt), id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move the project to another status (project manager or ADMIN)")
    public ProjectResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody StatusChangeRequest request) {
        return projectService.changeStatus(AuthenticatedUser.from(jwt), id, request.status());
    }

    @PutMapping("/{id}/manager")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a new project manager (ADMIN)")
    public ProjectResponse changeManager(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody UserIdRequest request) {
        return projectService.changeManager(AuthenticatedUser.from(jwt), id, request.userId());
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a member (project manager or ADMIN)")
    public ProjectResponse addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody UserIdRequest request) {
        return projectService.addMember(AuthenticatedUser.from(jwt), id, request.userId());
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member (project manager or ADMIN). The manager cannot be removed")
    public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
        projectService.removeMember(AuthenticatedUser.from(jwt), id, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a project without tasks (project manager or ADMIN)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        projectService.delete(AuthenticatedUser.from(jwt), id);
    }
}
