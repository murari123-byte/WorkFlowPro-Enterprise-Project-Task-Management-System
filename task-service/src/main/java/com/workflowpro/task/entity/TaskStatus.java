package com.workflowpro.task.entity;

import java.util.Set;

/**
 * Task workflow:
 * <pre>
 * TODO ⇄ IN_PROGRESS ⇄ IN_REVIEW ──► COMPLETED
 *   └────────┴────────────┴────────► CANCELLED
 * COMPLETED ──► IN_PROGRESS   (reopen)
 * CANCELLED ──► TODO          (restore)
 * </pre>
 * Who may make each move is decided in TaskPermissions.
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    COMPLETED,
    CANCELLED;

    public Set<TaskStatus> allowedNext() {
        return switch (this) {
            case TODO -> Set.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(TODO, IN_REVIEW, CANCELLED);
            case IN_REVIEW -> Set.of(IN_PROGRESS, COMPLETED, CANCELLED);
            case COMPLETED -> Set.of(IN_PROGRESS);
            case CANCELLED -> Set.of(TODO);
        };
    }

    public boolean canMoveTo(TaskStatus next) {
        return allowedNext().contains(next);
    }

    /** Done or cancelled: no longer "pending" and never overdue. */
    public boolean isClosed() {
        return this == COMPLETED || this == CANCELLED;
    }

    public static Set<TaskStatus> openStatuses() {
        return Set.of(TODO, IN_PROGRESS, IN_REVIEW);
    }
}
