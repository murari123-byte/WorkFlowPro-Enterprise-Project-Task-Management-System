package com.workflowpro.task.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.workflowpro.common.client.UserSummary;
import com.workflowpro.task.entity.TaskPriority;
import com.workflowpro.task.entity.TaskStatus;

/**
 * Full task details.
 *
 * @param allowedStatuses statuses the CALLER may move this task to right now
 */
public record TaskResponse(
        UUID id,
        UUID projectId,
        String projectName,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        boolean overdue,
        UserSummary assignee,
        UserSummary createdBy,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        List<TaskStatus> allowedStatuses,
        TaskPermissionsResponse permissions) {
}
