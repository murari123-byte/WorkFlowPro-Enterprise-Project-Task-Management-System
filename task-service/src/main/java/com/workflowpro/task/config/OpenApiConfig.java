package com.workflowpro.task.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/** Swagger UI: http://localhost:9083/swagger-ui.html - click "Authorize" and paste an access token. */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "WorkFlowPro Task Service API", version = "v1",
                description = "Tasks, assignment, task workflow, history, search and stats"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
