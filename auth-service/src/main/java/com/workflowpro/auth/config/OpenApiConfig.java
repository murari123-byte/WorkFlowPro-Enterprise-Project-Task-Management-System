package com.workflowpro.auth.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Swagger UI: http://localhost:9081/swagger-ui.html
 * Click "Authorize" and paste an access token to call protected endpoints.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "WorkFlowPro Auth Service API", version = "v1",
        description = "Registration, login, JWT tokens and current user"))
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
