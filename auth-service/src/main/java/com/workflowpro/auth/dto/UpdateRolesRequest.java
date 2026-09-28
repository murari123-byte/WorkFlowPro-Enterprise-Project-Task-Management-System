package com.workflowpro.auth.dto;

import java.util.Set;

import com.workflowpro.auth.entity.RoleName;

import jakarta.validation.constraints.NotEmpty;

/** ADMIN only. Replaces all roles of a user. Unknown role names are rejected with 400. */
public record UpdateRolesRequest(@NotEmpty Set<RoleName> roles) {
}
