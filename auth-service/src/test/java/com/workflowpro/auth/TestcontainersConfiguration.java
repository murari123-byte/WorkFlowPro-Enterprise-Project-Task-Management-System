package com.workflowpro.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts a temporary PostgreSQL container for tests.
 * {@code @ServiceConnection} points the datasource at it, so tests never touch the dev database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Test-only signing key. Real keys come from the JWT_SECRET environment variable. */
    public static final String TEST_JWT_SECRET_PROPERTY = "app.jwt.secret=test-only-secret-not-used-anywhere-else-1234567890";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
