package com.workflowpro.task.client;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.workflowpro.common.client.ServiceClients;
import com.workflowpro.common.exception.ResourceNotFoundException;
import com.workflowpro.task.config.ServiceUrlsProperties;

/**
 * Asks project-service about projects, with the caller's token. project-service decides
 * whether the caller can see a project, so task-service never has to copy membership data.
 */
@Component
public class ProjectClient {

    private static final String SERVICE_NAME = "project-service";

    private final RestClient restClient;

    public ProjectClient(ServiceUrlsProperties urls) {
        this.restClient = ServiceClients.create(urls.projectUrl());
    }

    /** @throws ResourceNotFoundException if the project does not exist or the caller is not a member */
    public ProjectMembership getMembership(UUID projectId) {
        return ServiceClients.call(SERVICE_NAME, () -> {
            try {
                return restClient.get().uri("/api/projects/{id}/membership", projectId)
                        .retrieve().body(ProjectMembership.class);
            } catch (HttpClientErrorException.NotFound e) {
                throw new ResourceNotFoundException("Project not found");
            }
        });
    }

    public AccessibleProjects getAccessibleProjects() {
        return ServiceClients.call(SERVICE_NAME, () -> restClient.get().uri("/api/projects/accessible")
                .retrieve().body(AccessibleProjects.class));
    }
}
