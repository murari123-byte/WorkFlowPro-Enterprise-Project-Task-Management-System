package com.workflowpro.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional first ADMIN account, created on startup if it does not exist yet.
 * Leave BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD empty to skip.
 */
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password, String firstName, String lastName) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
