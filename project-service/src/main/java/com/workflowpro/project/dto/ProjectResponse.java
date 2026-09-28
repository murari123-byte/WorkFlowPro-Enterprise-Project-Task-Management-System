package com.workflowpro.project.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.workflowpro.common.client.UserSummary;
import com.workflowpro.project.entity.ProjectStatus;

/**
 * Full project details.
 *
 * @param allowedStatuses status values the project may move to next (for the UI)
 * @param canManage       true if the caller may edit this project (ADMIN or its manager)
 */
public record ProjectResponse(
        UUID id,
        String name,
        String description,
        ProjectStatus status,
        List<ProjectStatus> allowedStatuses,
        LocalDate startDate,
        LocalDate endDate,
        UserSummary manager,
        List<UserSummary> members,
        boolean canManage,
        Instant createdAt,
        Instant updatedAt) {
}
