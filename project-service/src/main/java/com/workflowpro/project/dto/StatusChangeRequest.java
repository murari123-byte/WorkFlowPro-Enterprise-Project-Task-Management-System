package com.workflowpro.project.dto;

import com.workflowpro.project.entity.ProjectStatus;

import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull ProjectStatus status) {
}
