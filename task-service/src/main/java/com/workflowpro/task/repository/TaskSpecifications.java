package com.workflowpro.task.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.workflowpro.task.entity.Task;
import com.workflowpro.task.entity.TaskPriority;
import com.workflowpro.task.entity.TaskStatus;

/** WHERE-clause pieces for the task search. Unused filters are {@code unrestricted()}. */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> inProjects(Collection<UUID> projectIds) {
        return (root, query, cb) -> root.get("projectId").in(projectIds);
    }

    public static Specification<Task> inProject(UUID projectId) {
        return projectId == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
    }

    /** Case-insensitive match on title or description. */
    public static Specification<Task> matchesText(String search) {
        if (search == null || search.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern));
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return status == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(TaskPriority priority) {
        return priority == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> assignedTo(UUID assigneeId) {
        return assigneeId == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("assigneeId"), assigneeId);
    }

    public static Specification<Task> unassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assigneeId"));
    }

    /** Not completed and not cancelled. */
    public static Specification<Task> open() {
        return (root, query, cb) -> root.get("status").in(TaskStatus.openStatuses());
    }

    /** Due date in the past and still open. */
    public static Specification<Task> overdue(LocalDate today) {
        return (root, query, cb) -> cb.and(
                cb.lessThan(root.get("dueDate"), today),
                root.get("status").in(TaskStatus.openStatuses()));
    }
}
