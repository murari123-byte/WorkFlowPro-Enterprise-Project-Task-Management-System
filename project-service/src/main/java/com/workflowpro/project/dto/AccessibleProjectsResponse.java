package com.workflowpro.project.dto;

import java.util.List;
import java.util.UUID;

/**
 * Which projects the caller can see. Used by task-service to filter task lists and stats.
 *
 * @param allProjects true for ADMIN (projectIds is then empty and should be ignored)
 */
public record AccessibleProjectsResponse(boolean allProjects, List<UUID> projectIds) {
}
