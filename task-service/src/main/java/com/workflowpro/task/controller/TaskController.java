package com.workflowpro.task.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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

import com.workflowpro.common.exception.BadRequestException;
import com.workflowpro.common.security.AuthenticatedUser;
import com.workflowpro.common.web.PageResponse;
import com.workflowpro.common.web.Paging;
import com.workflowpro.task.dto.AssignTaskRequest;
import com.workflowpro.task.dto.CreateTaskRequest;
import com.workflowpro.task.dto.TaskCountResponse;
import com.workflowpro.task.dto.TaskHistoryResponse;
import com.workflowpro.task.dto.TaskResponse;
import com.workflowpro.task.dto.TaskStatsResponse;
import com.workflowpro.task.dto.TaskStatusRequest;
import com.workflowpro.task.dto.TaskSummaryResponse;
import com.workflowpro.task.dto.UpdateTaskRequest;
import com.workflowpro.task.entity.TaskPriority;
import com.workflowpro.task.entity.TaskStatus;
import com.workflowpro.task.service.TaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks")
public class TaskController {

    /** API sort name -> entity property. "priority" sorts by rank (LOW < ... < URGENT), not alphabetically. */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "title", "title",
            "status", "status",
            "priority", "priorityRank",
            "dueDate", "dueDate",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task in a project (project manager, team lead member or ADMIN)")
    public TaskResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(AuthenticatedUser.from(jwt), request);
    }

    @GetMapping
    @Operation(summary = "Search tasks I can see: text, status, priority, assignee, overdue, sorting, paging")
    public PageResponse<TaskSummaryResponse> search(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestParam(required = false) UUID projectId,
                                                    @RequestParam(required = false) String search,
                                                    @RequestParam(required = false) TaskStatus status,
                                                    @RequestParam(required = false) TaskPriority priority,
                                                    @RequestParam(required = false) UUID assigneeId,
                                                    @RequestParam(defaultValue = "false") boolean overdue,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) String sort) {
        return PageResponse.from(taskService.search(AuthenticatedUser.from(jwt), projectId, search, status, priority,
                assigneeId, overdue, Paging.pageable(page, size, sort, SORT_FIELDS, DEFAULT_SORT)), task -> task);
    }

    @GetMapping("/stats")
    @Operation(summary = "Task numbers for the dashboard (optionally for one project)")
    public TaskStatsResponse stats(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(required = false) UUID projectId) {
        return taskService.stats(AuthenticatedUser.from(jwt), projectId);
    }

    @GetMapping("/count")
    @Operation(summary = "Number of tasks in a project (used by project-service before deleting a project)")
    public TaskCountResponse count(@RequestParam UUID projectId) {
        return new TaskCountResponse(taskService.countForProject(projectId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Task details, including what I am allowed to do with it")
    public TaskResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return taskService.get(AuthenticatedUser.from(jwt), id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Activity timeline of a task, newest first")
    public PageResponse<TaskHistoryResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > Paging.MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be >= 0 and size between 1 and " + Paging.MAX_PAGE_SIZE);
        }
        return PageResponse.from(taskService.history(AuthenticatedUser.from(jwt), id, PageRequest.of(page, size)),
                entry -> entry);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit title, description, priority and due date")
    public TaskResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(AuthenticatedUser.from(jwt), id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move the task to another status (rules depend on your role and the workflow)")
    public TaskResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody TaskStatusRequest request) {
        return taskService.changeStatus(AuthenticatedUser.from(jwt), id, request.status());
    }

    @PutMapping("/{id}/assignee")
    @Operation(summary = "Assign the task to a project member, or unassign with assigneeId = null")
    public TaskResponse assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @RequestBody AssignTaskRequest request) {
        return taskService.assign(AuthenticatedUser.from(jwt), id, request.assigneeId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task (project manager or ADMIN)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        taskService.delete(AuthenticatedUser.from(jwt), id);
    }
}
