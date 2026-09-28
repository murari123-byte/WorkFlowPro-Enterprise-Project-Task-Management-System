package com.workflowpro.task.entity;

/** What happened to a task, recorded in task_history. */
public enum TaskAction {
    CREATED,
    UPDATED,
    ASSIGNED,
    STATUS_CHANGED,
    PRIORITY_CHANGED,
    DUE_DATE_CHANGED
}
