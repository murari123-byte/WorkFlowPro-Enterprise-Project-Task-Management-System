package com.workflowpro.project.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Body for "add member" and "change manager". */
public record UserIdRequest(@NotNull UUID userId) {
}
