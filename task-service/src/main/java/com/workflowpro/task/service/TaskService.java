package com.workflowpro.task.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workflowpro.common.client.RemoteUser;
import com.workflowpro.common.client.UserDirectoryClient;
import com.workflowpro.common.client.UserSummary;
import com.workflowpro.common.exception.BusinessRuleException;
import com.workflowpro.common.exception.ForbiddenException;
import com.workflowpro.common.exception.ResourceNotFoundException;
import com.workflowpro.common.security.AuthenticatedUser;
import com.workflowpro.task.client.AccessibleProjects;
import com.workflowpro.task.client.ProjectClient;
import com.workflowpro.task.client.ProjectMembership;
import com.workflowpro.task.dto.CreateTaskRequest;
import com.workflowpro.task.dto.TaskHistoryResponse;
import com.workflowpro.task.dto.TaskPermissionsResponse;
import com.workflowpro.task.dto.TaskResponse;
import com.workflowpro.task.dto.TaskStatsResponse;
import com.workflowpro.task.dto.TaskSummaryResponse;
import com.workflowpro.task.dto.UpdateTaskRequest;
import com.workflowpro.task.entity.Task;
import com.workflowpro.task.entity.TaskAction;
import com.workflowpro.task.entity.TaskHistory;
import com.workflowpro.task.entity.TaskPriority;
import com.workflowpro.task.entity.TaskStatus;
import com.workflowpro.task.repository.TaskHistoryRepository;
import com.workflowpro.task.repository.TaskRepository;
import com.workflowpro.task.repository.TaskSpecifications;

/**
 * Tasks always belong to a project owned by project-service. Before anything is read or
 * changed, the project's membership is fetched (with the caller's token). If project-service
 * says "not found", the caller is not a member and the task is reported as not found too.
 *
 * Every change writes a row to task_history in the SAME transaction as the change,
 * so the timeline can never disagree with the task.
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskHistoryRepository historyRepository;
    private final ProjectClient projectClient;
    private final UserDirectoryClient userDirectory;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, TaskHistoryRepository historyRepository,
                       ProjectClient projectClient, UserDirectoryClient userDirectory, Clock clock) {
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.projectClient = projectClient;
        this.userDirectory = userDirectory;
        this.clock = clock;
    }

    // ---------- create / read ----------

    @Transactional
    public TaskResponse create(AuthenticatedUser caller, CreateTaskRequest request) {
        ProjectMembership project = projectClient.getMembership(request.projectId());
        if (!TaskPermissions.canEdit(caller, project)) {
            throw new ForbiddenException("Only the project manager, a team lead of the project or an ADMIN can create tasks");
        }
        requireProjectOpen(project);

        TaskPriority priority = request.priority() == null ? TaskPriority.MEDIUM : request.priority();
        Task task = new Task(project.projectId(), request.title().trim(), trimToNull(request.description()),
                priority, request.dueDate(), caller.id());
        taskRepository.save(task);
        record(task, TaskAction.CREATED, null, null, task.getTitle(), caller);

        if (request.assigneeId() != null) {
            applyAssignment(caller, project, task, request.assigneeId());
        }
        return toResponse(caller, project, task);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(AuthenticatedUser caller, UUID taskId) {
        Task task = findTask(taskId);
        ProjectMembership project = membershipForTask(task);
        return toResponse(caller, project, task);
    }

    /**
     * Search tasks. With projectId: that project only (caller must be a member).
     * Without: every project the caller can see.
     */
    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> search(AuthenticatedUser caller, UUID projectId, String search,
                                            TaskStatus status, TaskPriority priority, UUID assigneeId,
                                            boolean overdueOnly, Pageable pageable) {
        Specification<Task> scope;
        if (projectId != null) {
            projectClient.getMembership(projectId); // 404 if the caller cannot see it
            scope = TaskSpecifications.inProject(projectId);
        } else {
            AccessibleProjects accessible = projectClient.getAccessibleProjects();
            if (!accessible.allProjects() && accessible.projectIds().isEmpty()) {
                return Page.empty(pageable);
            }
            scope = accessible.allProjects() ? Specification.unrestricted()
                    : TaskSpecifications.inProjects(accessible.projectIds());
        }

        Specification<Task> spec = Specification.allOf(
                scope,
                TaskSpecifications.matchesText(search),
                TaskSpecifications.hasStatus(status),
                TaskSpecifications.hasPriority(priority),
                TaskSpecifications.assignedTo(assigneeId),
                overdueOnly ? TaskSpecifications.overdue(today()) : Specification.unrestricted());
        Page<Task> page = taskRepository.findAll(spec, pageable);

        // One call to auth-service for all assignees on this page
        Set<UUID> assigneeIds = new HashSet<>();
        page.forEach(task -> {
            if (task.getAssigneeId() != null) {
                assigneeIds.add(task.getAssigneeId());
            }
        });
        Map<UUID, RemoteUser> users = userDirectory.findUsers(assigneeIds);
        LocalDate today = today();
        return page.map(task -> new TaskSummaryResponse(task.getId(), task.getProjectId(), task.getTitle(),
                task.getStatus(), task.getPriority(), task.getDueDate(), task.isOverdue(today),
                assigneeSummary(task, users), task.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public Page<TaskHistoryResponse> history(AuthenticatedUser caller, UUID taskId, Pageable pageable) {
        Task task = findTask(taskId);
        membershipForTask(task);
        Page<TaskHistory> page = historyRepository.findByTaskIdOrderByCreatedAtDescIdDesc(taskId, pageable);

        Set<UUID> actorIds = new HashSet<>();
        page.forEach(entry -> actorIds.add(entry.getActorId()));
        Map<UUID, RemoteUser> users = userDirectory.findUsers(actorIds);
        return page.map(entry -> new TaskHistoryResponse(entry.getId(), entry.getAction(), entry.getField(),
                entry.getOldValue(), entry.getNewValue(), UserDirectoryClient.summaryOf(entry.getActorId(), users),
                entry.getCreatedAt()));
    }

    // ---------- change ----------

    @Transactional
    public TaskResponse update(AuthenticatedUser caller, UUID taskId, UpdateTaskRequest request) {
        Task task = findTask(taskId);
        ProjectMembership project = membershipForTask(task);
        if (!TaskPermissions.canEdit(caller, project)) {
            throw new ForbiddenException("Only the project manager, a team lead of the project or an ADMIN can edit tasks");
        }
        requireProjectOpen(project);

        String title = request.title().trim();
        String description = trimToNull(request.description());
        if (!request.priority().equals(task.getPriority())) {
            record(task, TaskAction.PRIORITY_CHANGED, "priority", task.getPriority().name(),
                    request.priority().name(), caller);
            task.setPriority(request.priority());
        }
        if (!Objects.equals(request.dueDate(), task.getDueDate())) {
            if (request.dueDate() != null && request.dueDate().isBefore(today())) {
                throw new BusinessRuleException("dueDate cannot be moved into the past");
            }
            record(task, TaskAction.DUE_DATE_CHANGED, "dueDate", Objects.toString(task.getDueDate(), null),
                    Objects.toString(request.dueDate(), null), caller);
            task.setDueDate(request.dueDate());
        }
        if (!title.equals(task.getTitle())) {
            record(task, TaskAction.UPDATED, "title", task.getTitle(), title, caller);
            task.setTitle(title);
        }
        if (!Objects.equals(description, task.getDescription())) {
            record(task, TaskAction.UPDATED, "description", task.getDescription(), description, caller);
            task.setDescription(description);
        }
        return toResponse(caller, project, task);
    }

    @Transactional
    public TaskResponse changeStatus(AuthenticatedUser caller, UUID taskId, TaskStatus newStatus) {
        Task task = findTask(taskId);
        ProjectMembership project = membershipForTask(task);
        requireProjectOpen(project);
        if (!task.getStatus().canMoveTo(newStatus)) {
            throw new BusinessRuleException("Cannot change task status from " + task.getStatus() + " to "
                    + newStatus + ". Allowed: " + task.getStatus().allowedNext());
        }
        if (!TaskPermissions.canChangeStatus(caller, project, task, newStatus)) {
            throw new ForbiddenException("You are not allowed to move this task to " + newStatus);
        }
        record(task, TaskAction.STATUS_CHANGED, "status", task.getStatus().name(), newStatus.name(), caller);
        task.changeStatus(newStatus, clock.instant());
        return toResponse(caller, project, task);
    }

    /** assigneeId = null removes the assignee. */
    @Transactional
    public TaskResponse assign(AuthenticatedUser caller, UUID taskId, UUID assigneeId) {
        Task task = findTask(taskId);
        ProjectMembership project = membershipForTask(task);
        if (!TaskPermissions.canEdit(caller, project)) {
            throw new ForbiddenException("Only the project manager, a team lead of the project or an ADMIN can assign tasks");
        }
        requireProjectOpen(project);
        if (task.getStatus().isClosed()) {
            throw new BusinessRuleException("A " + task.getStatus() + " task cannot be reassigned");
        }
        applyAssignment(caller, project, task, assigneeId);
        return toResponse(caller, project, task);
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID taskId) {
        Task task = findTask(taskId);
        ProjectMembership project = membershipForTask(task);
        if (!TaskPermissions.canDelete(caller, project)) {
            throw new ForbiddenException("Only the project manager or an ADMIN can delete tasks");
        }
        taskRepository.delete(task); // history rows are removed by ON DELETE CASCADE
    }

    // ---------- numbers ----------

    /** For project-service: may this project be deleted? */
    @Transactional(readOnly = true)
    public long countForProject(UUID projectId) {
        projectClient.getMembership(projectId);
        return taskRepository.countByProjectId(projectId);
    }

    /** Dashboard. projectId = null means every project the caller can see. */
    @Transactional(readOnly = true)
    public TaskStatsResponse stats(AuthenticatedUser caller, UUID projectId) {
        Set<TaskStatus> open = TaskStatus.openStatuses();
        List<Object[]> statusRows;
        List<Object[]> priorityRows;
        long overdue;

        List<UUID> projectIds = null; // null = all projects (ADMIN, no project filter)
        if (projectId != null) {
            projectClient.getMembership(projectId);
            projectIds = List.of(projectId);
        } else {
            AccessibleProjects accessible = projectClient.getAccessibleProjects();
            if (!accessible.allProjects()) {
                projectIds = accessible.projectIds();
            }
        }

        if (projectIds == null) {
            statusRows = taskRepository.countByStatus();
            priorityRows = taskRepository.countByPriority();
            overdue = taskRepository.countOverdue(today(), open);
        } else if (projectIds.isEmpty()) {
            statusRows = List.of();
            priorityRows = List.of();
            overdue = 0;
        } else {
            statusRows = taskRepository.countByStatusInProjects(projectIds);
            priorityRows = taskRepository.countByPriorityInProjects(projectIds);
            overdue = taskRepository.countOverdueInProjects(projectIds, today(), open);
        }

        Map<TaskStatus, Long> byStatus = zeroFilled(TaskStatus.class);
        statusRows.forEach(row -> byStatus.put((TaskStatus) row[0], (Long) row[1]));
        Map<TaskPriority, Long> byPriority = zeroFilled(TaskPriority.class);
        priorityRows.forEach(row -> byPriority.put((TaskPriority) row[0], (Long) row[1]));

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long pending = open.stream().mapToLong(byStatus::get).sum();
        return new TaskStatsResponse(total, pending, byStatus.get(TaskStatus.COMPLETED), overdue,
                taskRepository.countOpenAssignedTo(caller.id(), open), byStatus, byPriority);
    }

    // ---------- helpers ----------

    private void applyAssignment(AuthenticatedUser caller, ProjectMembership project, Task task, UUID assigneeId) {
        if (Objects.equals(assigneeId, task.getAssigneeId())) {
            return;
        }
        String oldName = task.getAssigneeId() == null ? null : displayName(task.getAssigneeId());
        String newName = null;
        if (assigneeId != null) {
            if (!project.isMember(assigneeId)) {
                throw new BusinessRuleException("The assignee must be a member of the project");
            }
            RemoteUser assignee = userDirectory.findUser(assigneeId)
                    .filter(RemoteUser::enabled)
                    .orElseThrow(() -> new BusinessRuleException("Assignee not found or account disabled"));
            newName = assignee.firstName() + " " + assignee.lastName();
        }
        record(task, TaskAction.ASSIGNED, "assignee", oldName, newName, caller);
        task.setAssigneeId(assigneeId);
    }

    private String displayName(UUID userId) {
        return userDirectory.findUser(userId).map(user -> user.firstName() + " " + user.lastName())
                .orElse("Unknown user");
    }

    private Task findTask(UUID taskId) {
        return taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    /** Non-members must not learn that the task exists: "project not found" becomes "task not found". */
    private ProjectMembership membershipForTask(Task task) {
        try {
            return projectClient.getMembership(task.getProjectId());
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Task not found");
        }
    }

    private static void requireProjectOpen(ProjectMembership project) {
        if (!project.acceptsTaskChanges()) {
            throw new BusinessRuleException("Project is " + project.status() + ": its tasks cannot be changed");
        }
    }

    private void record(Task task, TaskAction action, String field, String oldValue, String newValue,
                        AuthenticatedUser caller) {
        historyRepository.save(new TaskHistory(task.getId(), action, field, oldValue, newValue, caller.id(),
                clock.instant()));
    }

    private TaskResponse toResponse(AuthenticatedUser caller, ProjectMembership project, Task task) {
        Set<UUID> ids = new HashSet<>();
        ids.add(task.getCreatedBy());
        if (task.getAssigneeId() != null) {
            ids.add(task.getAssigneeId());
        }
        Map<UUID, RemoteUser> users = userDirectory.findUsers(ids);
        boolean open = project.acceptsTaskChanges();
        TaskPermissionsResponse permissions = new TaskPermissionsResponse(
                open && TaskPermissions.canEdit(caller, project),
                open && TaskPermissions.canEdit(caller, project) && !task.getStatus().isClosed(),
                TaskPermissions.canDelete(caller, project));

        return new TaskResponse(task.getId(), task.getProjectId(), project.name(), task.getTitle(),
                task.getDescription(), task.getStatus(), task.getPriority(), task.getDueDate(),
                task.isOverdue(today()), assigneeSummary(task, users),
                UserDirectoryClient.summaryOf(task.getCreatedBy(), users), task.getCompletedAt(),
                createdAtOrNow(task), task.getUpdatedAt(), TaskPermissions.allowedStatuses(caller, project, task),
                permissions);
    }

    private static UserSummary assigneeSummary(Task task, Map<UUID, RemoteUser> users) {
        return task.getAssigneeId() == null ? null : UserDirectoryClient.summaryOf(task.getAssigneeId(), users);
    }

    /** @CreationTimestamp is filled on flush; a just-created task may not have it yet. */
    private Instant createdAtOrNow(Task task) {
        return task.getCreatedAt() == null ? clock.instant() : task.getCreatedAt();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static <E extends Enum<E>> Map<E, Long> zeroFilled(Class<E> type) {
        Map<E, Long> map = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            map.put(value, 0L);
        }
        return map;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
