package com.workflowpro.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reference data seeded by Flyway (V2). The application never inserts roles.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private RoleName name;

    protected Role() {
        // required by JPA
    }

    public Short getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }
}
