package com.workflowpro.task.dto;

import java.util.Map;

import com.workflowpro.task.entity.TaskPriority;
import com.workflowpro.task.entity.TaskStatus;

/**
 * Dashboard numbers for the tasks the caller can see.
 *
 * @param pending     TODO + IN_PROGRESS + IN_REVIEW
 * @param myOpenTasks open tasks assigned to the caller
 */
public record TaskStatsResponse(
        long total,
        long pending,
        long completed,
        long overdue,
        long myOpenTasks,
        Map<TaskStatus, Long> byStatus,
        Map<TaskPriority, Long> byPriority) {
}
