package com.workflowpro.project.client;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.workflowpro.common.client.ServiceClients;
import com.workflowpro.project.config.ServiceUrlsProperties;

/** Calls task-service. Only used to stop a project with tasks from being deleted. */
@Component
public class TaskClient {

    private static final String SERVICE_NAME = "task-service";

    private final RestClient restClient;

    public TaskClient(ServiceUrlsProperties urls) {
        this.restClient = ServiceClients.create(urls.taskUrl());
    }

    public long countTasks(UUID projectId) {
        TaskCount count = ServiceClients.call(SERVICE_NAME, () -> restClient.get()
                .uri(uri -> uri.path("/api/tasks/count").queryParam("projectId", projectId).build())
                .retrieve().body(TaskCount.class));
        return count == null ? 0 : count.count();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TaskCount(long count) {
    }
}
