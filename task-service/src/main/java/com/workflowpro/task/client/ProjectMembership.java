package com.workflowpro.task.client;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** project-service's /api/projects/{id}/membership response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectMembership(UUID projectId, String name, String status, UUID managerId, List<UUID> memberIds) {

    /** Tasks can only be created or changed while the project is PLANNING or ACTIVE. */
    public boolean acceptsTaskChanges() {
        return "PLANNING".equals(status) || "ACTIVE".equals(status);
    }

    public boolean isMember(UUID userId) {
        return memberIds != null && memberIds.contains(userId);
    }

    public boolean isManager(UUID userId) {
        return managerId != null && managerId.equals(userId);
    }
}
