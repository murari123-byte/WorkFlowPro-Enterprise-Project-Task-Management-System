package com.workflowpro.task.client;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** project-service's /api/projects/accessible response. allProjects = true for ADMIN. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccessibleProjects(boolean allProjects, List<UUID> projectIds) {
}
