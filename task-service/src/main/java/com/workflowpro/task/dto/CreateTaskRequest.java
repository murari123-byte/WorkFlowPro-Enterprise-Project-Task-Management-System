package com.workflowpro.task.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.workflowpro.task.entity.TaskPriority;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param priority   optional, defaults to MEDIUM
 * @param assigneeId optional; must be a member of the project
 */
public record CreateTaskRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        TaskPriority priority,
        @FutureOrPresent(message = "dueDate cannot be in the past") LocalDate dueDate,
        UUID assigneeId) {
}
