package com.workflowpro.project.entity;

import java.util.Set;

/**
 * Project workflow:
 * <pre>
 * PLANNING ──► ACTIVE ──► COMPLETED
 *    │           │  ▲
 *    │           ▼  │
 *    │         ON_HOLD
 *    └──────────┴────────► CANCELLED
 * </pre>
 * COMPLETED and CANCELLED are final: the project becomes read-only.
 */
public enum ProjectStatus {
    PLANNING,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    CANCELLED;

    public Set<ProjectStatus> allowedNext() {
        return switch (this) {
            case PLANNING -> Set.of(ACTIVE, CANCELLED);
            case ACTIVE -> Set.of(ON_HOLD, COMPLETED, CANCELLED);
            case ON_HOLD -> Set.of(ACTIVE, CANCELLED);
            case COMPLETED, CANCELLED -> Set.of();
        };
    }

    public boolean canMoveTo(ProjectStatus next) {
        return allowedNext().contains(next);
    }

    /** Completed or cancelled projects cannot be edited and accept no new tasks. */
    public boolean isClosed() {
        return this == COMPLETED || this == CANCELLED;
    }
}
