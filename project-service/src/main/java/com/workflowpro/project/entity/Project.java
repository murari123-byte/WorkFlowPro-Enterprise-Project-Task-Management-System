package com.workflowpro.project.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.PLANNING;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** User id from auth-service. The manager is always also a member. */
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    /** Loaded only when needed (LAZY) - lists of projects never touch this table. */
    @ElementCollection
    @CollectionTable(name = "project_members", joinColumns = @JoinColumn(name = "project_id"))
    private Set<ProjectMember> members = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Two people saving the same project at once: the second save fails with 409 instead of overwriting. */
    @Version
    private long version;

    protected Project() {
        // required by JPA
    }

    public Project(String name, String description, LocalDate startDate, LocalDate endDate, UUID managerId,
                   UUID createdBy) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
        changeManager(managerId);
    }

    public void updateDetails(String name, String description, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void changeStatus(ProjectStatus status) {
        this.status = status;
    }

    /** The new manager automatically becomes a member; the old manager stays a member. */
    public void changeManager(UUID newManagerId) {
        this.managerId = newManagerId;
        addMember(newManagerId);
    }

    /** @return false if the user was already a member */
    public boolean addMember(UUID userId) {
        return members.add(new ProjectMember(userId, Instant.now()));
    }

    /** @return false if the user was not a member */
    public boolean removeMember(UUID userId) {
        return members.removeIf(member -> member.getUserId().equals(userId));
    }

    public boolean isMember(UUID userId) {
        return members.stream().anyMatch(member -> member.getUserId().equals(userId));
    }

    public boolean isManager(UUID userId) {
        return managerId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Set<ProjectMember> getMembers() {
        return members;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
