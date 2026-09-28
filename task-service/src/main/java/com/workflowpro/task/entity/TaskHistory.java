package com.workflowpro.task.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One entry in a task's activity timeline. Written once, never changed.
 * Values are stored as display text (e.g. the assignee's name at that moment),
 * because history should show what was true then.
 */
@Entity
@Table(name = "task_history")
public class TaskHistory {

    private static final int MAX_VALUE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TaskAction action;

    @Column(length = 30, updatable = false)
    private String field;

    @Column(name = "old_value", length = MAX_VALUE_LENGTH, updatable = false)
    private String oldValue;

    @Column(name = "new_value", length = MAX_VALUE_LENGTH, updatable = false)
    private String newValue;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskHistory() {
        // required by JPA
    }

    public TaskHistory(UUID taskId, TaskAction action, String field, String oldValue, String newValue,
                       UUID actorId, Instant createdAt) {
        this.taskId = taskId;
        this.action = action;
        this.field = field;
        this.oldValue = truncate(oldValue);
        this.newValue = truncate(newValue);
        this.actorId = actorId;
        this.createdAt = createdAt;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH - 3) + "...";
    }

    public Long getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public TaskAction getAction() {
        return action;
    }

    public String getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public UUID getActorId() {
        return actorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
