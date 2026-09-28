package com.workflowpro.project.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One row of project_members. It is a value owned by its Project (no own id or lifecycle),
 * so it is mapped as an @Embeddable inside an @ElementCollection.
 * Two members are equal when they are the same user.
 */
@Embeddable
public class ProjectMember {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected ProjectMember() {
        // required by JPA
    }

    public ProjectMember(UUID userId, Instant addedAt) {
        this.userId = userId;
        this.addedAt = addedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProjectMember member && Objects.equals(userId, member.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }
}
