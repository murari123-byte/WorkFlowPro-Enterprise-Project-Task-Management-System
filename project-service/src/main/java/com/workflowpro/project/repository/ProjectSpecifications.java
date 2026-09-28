package com.workflowpro.project.repository;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.workflowpro.project.entity.Project;
import com.workflowpro.project.entity.ProjectMember;
import com.workflowpro.project.entity.ProjectStatus;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** WHERE-clause pieces for the project list. Unused filters are {@code unrestricted()}. */
public final class ProjectSpecifications {

    private ProjectSpecifications() {
    }

    /** Case-insensitive match on name or description. */
    public static Specification<Project> matchesText(String search) {
        if (search == null || search.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("description")), pattern));
    }

    public static Specification<Project> hasStatus(ProjectStatus status) {
        return status == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Project> hasManager(UUID managerId) {
        return managerId == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("managerId"), managerId);
    }

    /** Only projects the user is a member of (EXISTS subquery, so no duplicate rows). */
    public static Specification<Project> visibleTo(UUID userId) {
        return (root, query, cb) -> {
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Project> sub = subquery.from(Project.class);
            Join<Project, ProjectMember> members = sub.join("members");
            subquery.select(cb.literal(1))
                    .where(cb.equal(sub.get("id"), root.get("id")), cb.equal(members.get("userId"), userId));
            return cb.exists(subquery);
        };
    }
}
