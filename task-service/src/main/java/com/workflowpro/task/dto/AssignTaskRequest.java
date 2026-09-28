package com.workflowpro.task.dto;

import java.util.UUID;

/** assigneeId = null removes the assignee. */
public record AssignTaskRequest(UUID assigneeId) {
}
