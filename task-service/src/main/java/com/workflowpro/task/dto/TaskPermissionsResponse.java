package com.workflowpro.task.dto;

/** What the caller may do with this task, so the UI can show/hide buttons. The API still checks every call. */
public record TaskPermissionsResponse(boolean canEdit, boolean canAssign, boolean canDelete) {
}
