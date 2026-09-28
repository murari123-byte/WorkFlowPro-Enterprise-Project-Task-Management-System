package com.workflowpro.task.entity;

/** Stored as text; the database also keeps a numeric priority_rank (1-4) for correct sorting. */
public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
