package com.workflowpro.task.dto;

import java.time.LocalDate;

import com.workflowpro.task.entity.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces the editable fields. Status and assignee have their own endpoints. */
public record UpdateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull TaskPriority priority,
        LocalDate dueDate) {
}
