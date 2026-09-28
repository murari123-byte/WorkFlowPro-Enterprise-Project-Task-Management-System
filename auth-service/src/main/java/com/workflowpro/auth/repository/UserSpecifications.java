package com.workflowpro.auth.repository;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.workflowpro.auth.entity.Role;
import com.workflowpro.auth.entity.RoleName;
import com.workflowpro.auth.entity.User;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;

/**
 * Reusable WHERE-clause pieces for searching users. When a filter is not used, the piece is
 * {@code Specification.unrestricted()} ("no condition"). Spring Data 4 does not accept null here.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** Case-insensitive match on email, first name or last name. */
    public static Specification<User> matchesText(String search) {
        if (search == null || search.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern));
    }

    /** Users that have the given role. A subquery avoids duplicate rows from the join. */
    public static Specification<User> hasRole(RoleName role) {
        if (role == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            var subRoot = subquery.from(User.class);
            Join<User, Role> roles = subRoot.join("roles");
            subquery.select(cb.literal(1L))
                    .where(cb.equal(subRoot.get("id"), root.get("id")), cb.equal(roles.get("name"), role));
            return cb.exists(subquery);
        };
    }

    public static Specification<User> enabledOnly(boolean onlyEnabled) {
        return onlyEnabled ? (root, query, cb) -> cb.isTrue(root.get("enabled")) : Specification.unrestricted();
    }
}
