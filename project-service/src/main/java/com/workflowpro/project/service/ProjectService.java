package com.workflowpro.project.service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workflowpro.common.exception.BusinessRuleException;
import com.workflowpro.common.exception.ConflictException;
import com.workflowpro.common.exception.ForbiddenException;
import com.workflowpro.common.exception.ResourceNotFoundException;
import com.workflowpro.common.security.AuthenticatedUser;
import com.workflowpro.common.client.RemoteUser;
import com.workflowpro.project.client.TaskClient;
import com.workflowpro.common.client.UserDirectoryClient;
import com.workflowpro.project.dto.AccessibleProjectsResponse;
import com.workflowpro.project.dto.ProjectMembershipResponse;
import com.workflowpro.project.dto.ProjectRequest;
import com.workflowpro.project.dto.ProjectResponse;
import com.workflowpro.project.dto.ProjectStatsResponse;
import com.workflowpro.project.dto.ProjectSummaryResponse;
import com.workflowpro.common.client.UserSummary;
import com.workflowpro.project.entity.Project;
import com.workflowpro.project.entity.ProjectMember;
import com.workflowpro.project.entity.ProjectStatus;
import com.workflowpro.project.repository.ProjectRepository;
import com.workflowpro.project.repository.ProjectSpecifications;

/**
 * Project rules:
 * - ADMIN and PROJECT_MANAGER create projects (checked with @PreAuthorize on the controller).
 * - Members see a project. Non-members get 404, so they cannot even tell it exists. ADMIN sees all.
 * - Only the project's manager or an ADMIN edits it, changes its status or its members.
 * - Only an ADMIN hands a project to another manager.
 * - COMPLETED / CANCELLED projects are read-only.
 * - A project that still has tasks cannot be deleted.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserDirectoryClient userDirectory;
    private final TaskClient taskClient;

    public ProjectService(ProjectRepository projectRepository, UserDirectoryClient userDirectory, TaskClient taskClient) {
        this.projectRepository = projectRepository;
        this.userDirectory = userDirectory;
        this.taskClient = taskClient;
    }

    @Transactional
    public ProjectResponse create(AuthenticatedUser caller, ProjectRequest request) {
        UUID managerId = request.managerId() == null ? caller.id() : request.managerId();
        if (!managerId.equals(caller.id()) && !caller.isAdmin()) {
            throw new ForbiddenException("Only an ADMIN can create a project for another manager");
        }
        String name = request.name().trim();
        if (projectRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A project with this name already exists");
        }
        requireValidManager(managerId);

        Project project = new Project(name, trimToNull(request.description()), request.startDate(),
                request.endDate(), managerId, caller.id());
        projectRepository.save(project);
        return toResponse(caller, project);
    }

    @Transactional(readOnly = true)
    public Page<ProjectSummaryResponse> list(AuthenticatedUser caller, String search, ProjectStatus status,
                                             UUID managerId, Pageable pageable) {
        Specification<Project> spec = Specification.allOf(
                caller.isAdmin() ? Specification.unrestricted() : ProjectSpecifications.visibleTo(caller.id()),
                ProjectSpecifications.matchesText(search),
                ProjectSpecifications.hasStatus(status),
                ProjectSpecifications.hasManager(managerId));
        Page<Project> page = projectRepository.findAll(spec, pageable);

        // One call to auth-service for all managers on this page (not one call per row)
        Set<UUID> managerIds = new HashSet<>();
        page.forEach(project -> managerIds.add(project.getManagerId()));
        Map<UUID, RemoteUser> managers = userDirectory.findUsers(managerIds);

        return page.map(project -> new ProjectSummaryResponse(project.getId(), project.getName(),
                project.getStatus(), project.getStartDate(), project.getEndDate(),
                UserDirectoryClient.summaryOf(project.getManagerId(), managers), project.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(AuthenticatedUser caller, UUID projectId) {
        return toResponse(caller, findVisible(caller, projectId));
    }

    @Transactional
    public ProjectResponse update(AuthenticatedUser caller, UUID projectId, ProjectRequest request) {
        Project project = findVisible(caller, projectId);
        requireCanManage(caller, project);
        requireOpen(project);
        String name = request.name().trim();
        if (projectRepository.existsByNameIgnoreCaseAndIdNot(name, projectId)) {
            throw new ConflictException("A project with this name already exists");
        }
        project.updateDetails(name, trimToNull(request.description()), request.startDate(), request.endDate());
        return toResponse(caller, project);
    }

    @Transactional
    public ProjectResponse changeStatus(AuthenticatedUser caller, UUID projectId, ProjectStatus newStatus) {
        Project project = findVisible(caller, projectId);
        requireCanManage(caller, project);
        if (!project.getStatus().canMoveTo(newStatus)) {
            throw new BusinessRuleException("Cannot change project status from " + project.getStatus()
                    + " to " + newStatus + ". Allowed: " + project.getStatus().allowedNext());
        }
        project.changeStatus(newStatus);
        return toResponse(caller, project);
    }

    /** ADMIN only (@PreAuthorize on the controller). */
    @Transactional
    public ProjectResponse changeManager(AuthenticatedUser caller, UUID projectId, UUID newManagerId) {
        Project project = findVisible(caller, projectId);
        requireOpen(project);
        requireValidManager(newManagerId);
        project.changeManager(newManagerId);
        return toResponse(caller, project);
    }

    @Transactional
    public ProjectResponse addMember(AuthenticatedUser caller, UUID projectId, UUID userId) {
        Project project = findVisible(caller, projectId);
        requireCanManage(caller, project);
        requireOpen(project);
        if (project.isMember(userId)) {
            throw new ConflictException("User is already a member of this project");
        }
        RemoteUser user = userDirectory.findUser(userId)
                .filter(RemoteUser::enabled)
                .orElseThrow(() -> new BusinessRuleException("User not found or account disabled"));
        project.addMember(user.id());
        return toResponse(caller, project);
    }

    @Transactional
    public void removeMember(AuthenticatedUser caller, UUID projectId, UUID userId) {
        Project project = findVisible(caller, projectId);
        requireCanManage(caller, project);
        requireOpen(project);
        if (project.isManager(userId)) {
            throw new BusinessRuleException("The project manager cannot be removed. Assign a new manager first");
        }
        if (!project.removeMember(userId)) {
            throw new ResourceNotFoundException("User is not a member of this project");
        }
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID projectId) {
        Project project = findVisible(caller, projectId);
        requireCanManage(caller, project);
        long taskCount = taskClient.countTasks(projectId);
        if (taskCount > 0) {
            throw new ConflictException("Project still has " + taskCount
                    + " task(s). Delete them first, or set the project to CANCELLED instead");
        }
        projectRepository.delete(project);
    }

    /** For task-service: project status, manager and member ids. 404 unless the caller can see it. */
    @Transactional(readOnly = true)
    public ProjectMembershipResponse membership(AuthenticatedUser caller, UUID projectId) {
        Project project = findVisible(caller, projectId);
        List<UUID> memberIds = project.getMembers().stream().map(ProjectMember::getUserId).toList();
        return new ProjectMembershipResponse(project.getId(), project.getName(), project.getStatus(),
                project.getManagerId(), memberIds);
    }

    @Transactional(readOnly = true)
    public AccessibleProjectsResponse accessibleProjects(AuthenticatedUser caller) {
        if (caller.isAdmin()) {
            return new AccessibleProjectsResponse(true, List.of());
        }
        return new AccessibleProjectsResponse(false, projectRepository.findIdsByMember(caller.id()));
    }

    @Transactional(readOnly = true)
    public ProjectStatsResponse stats(AuthenticatedUser caller) {
        List<Object[]> rows = caller.isAdmin()
                ? projectRepository.countByStatus()
                : projectRepository.countByStatusForMember(caller.id());

        Map<ProjectStatus, Long> byStatus = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatus status : ProjectStatus.values()) {
            byStatus.put(status, 0L);
        }
        rows.forEach(row -> byStatus.put((ProjectStatus) row[0], (Long) row[1]));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new ProjectStatsResponse(total, byStatus.get(ProjectStatus.ACTIVE),
                byStatus.get(ProjectStatus.COMPLETED), byStatus);
    }

    // ---------- rules ----------

    private Project findVisible(AuthenticatedUser caller, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!caller.isAdmin() && !project.isMember(caller.id())) {
            throw new ResourceNotFoundException("Project not found");
        }
        return project;
    }

    private static boolean canManage(AuthenticatedUser caller, Project project) {
        return caller.isAdmin() || project.isManager(caller.id());
    }

    private static void requireCanManage(AuthenticatedUser caller, Project project) {
        if (!canManage(caller, project)) {
            throw new ForbiddenException("Only the project manager or an ADMIN can change this project");
        }
    }

    private static void requireOpen(Project project) {
        if (project.getStatus().isClosed()) {
            throw new BusinessRuleException("Project is " + project.getStatus() + " and can no longer be changed");
        }
    }

    /** A manager must be an enabled user with the PROJECT_MANAGER or ADMIN role. */
    private void requireValidManager(UUID managerId) {
        RemoteUser manager = userDirectory.findUser(managerId)
                .filter(RemoteUser::enabled)
                .orElseThrow(() -> new BusinessRuleException("Manager not found or account disabled"));
        if (!manager.hasAnyRole(AuthenticatedUser.PROJECT_MANAGER, AuthenticatedUser.ADMIN)) {
            throw new BusinessRuleException("The manager must have the PROJECT_MANAGER or ADMIN role");
        }
    }

    // ---------- mapping ----------

    private ProjectResponse toResponse(AuthenticatedUser caller, Project project) {
        Set<UUID> userIds = new HashSet<>();
        project.getMembers().forEach(member -> userIds.add(member.getUserId()));
        userIds.add(project.getManagerId());
        Map<UUID, RemoteUser> users = userDirectory.findUsers(userIds);

        List<UserSummary> members = project.getMembers().stream()
                .map(member -> UserDirectoryClient.summaryOf(member.getUserId(), users))
                .sorted(Comparator.comparing(UserSummary::firstName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(UserSummary::lastName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        boolean canManage = canManage(caller, project);
        List<ProjectStatus> allowedStatuses = canManage
                ? project.getStatus().allowedNext().stream().sorted().toList()
                : List.of();

        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                project.getStatus(), allowedStatuses, project.getStartDate(), project.getEndDate(),
                UserDirectoryClient.summaryOf(project.getManagerId(), users), members, canManage,
                project.getCreatedAt(), project.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
