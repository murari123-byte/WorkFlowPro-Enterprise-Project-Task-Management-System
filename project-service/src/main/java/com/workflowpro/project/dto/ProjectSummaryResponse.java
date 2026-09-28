package com.workflowpro.project.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.workflowpro.common.client.UserSummary;
import com.workflowpro.project.entity.ProjectStatus;

/** One row of the project list. No member list, to keep list responses small. */
public record ProjectSummaryResponse(
        UUID id,
        String name,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate,
        UserSummary manager,
        Instant updatedAt) {
}
