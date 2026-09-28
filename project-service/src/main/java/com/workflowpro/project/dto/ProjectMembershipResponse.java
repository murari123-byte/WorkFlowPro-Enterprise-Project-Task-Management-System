package com.workflowpro.project.dto;

import java.util.List;
import java.util.UUID;

import com.workflowpro.project.entity.ProjectStatus;

/**
 * Small, internal view used by task-service to make its permission decisions
 * (no names, no extra calls to auth-service).
 */
public record ProjectMembershipResponse(
        UUID projectId,
        String name,
        ProjectStatus status,
        UUID managerId,
        List<UUID> memberIds) {
}
