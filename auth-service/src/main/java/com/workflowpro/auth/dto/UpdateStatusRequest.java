package com.workflowpro.auth.dto;

import jakarta.validation.constraints.NotNull;

/** ADMIN only. enabled=false blocks login and refresh for that user. */
public record UpdateStatusRequest(@NotNull Boolean enabled) {
}
