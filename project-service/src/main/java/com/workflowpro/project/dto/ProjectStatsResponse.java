package com.workflowpro.project.dto;

import java.util.Map;

import com.workflowpro.project.entity.ProjectStatus;

/** Dashboard numbers for the projects the caller can see. byStatus always has all 5 statuses. */
public record ProjectStatsResponse(
        long total,
        long active,
        long completed,
        Map<ProjectStatus, Long> byStatus) {
}
