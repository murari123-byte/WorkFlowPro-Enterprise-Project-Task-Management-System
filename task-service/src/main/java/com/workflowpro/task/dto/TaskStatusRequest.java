package com.workflowpro.task.dto;

import com.workflowpro.task.entity.TaskStatus;

import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(@NotNull TaskStatus status) {
}
