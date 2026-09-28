package com.workflowpro.task.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.workflowpro.common.client.UserSummary;
import com.workflowpro.task.entity.TaskPriority;
import com.workflowpro.task.entity.TaskStatus;

/** One row of a task list (no description, no history, to keep lists small). assignee may be null. */
public record TaskSummaryResponse(
        UUID id,
        UUID projectId,
        String title,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        boolean overdue,
        UserSummary assignee,
        Instant updatedAt) {
}
