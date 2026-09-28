package com.workflowpro.task.dto;

import java.time.Instant;

import com.workflowpro.common.client.UserSummary;
import com.workflowpro.task.entity.TaskAction;

public record TaskHistoryResponse(
        Long id,
        TaskAction action,
        String field,
        String oldValue,
        String newValue,
        UserSummary actor,
        Instant createdAt) {
}
