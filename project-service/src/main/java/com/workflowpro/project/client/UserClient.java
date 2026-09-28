package com.workflowpro.project.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.workflowpro.common.client.ServiceClients;
import com.workflowpro.project.config.ServiceUrlsProperties;

/** Calls auth-service's /api/users endpoints with the caller's token. */
@Component
public class UserClient {

    private static final String SERVICE_NAME = "auth-service";
    private static final int BATCH_SIZE = 100;

    private final RestClient restClient;

    public UserClient(ServiceUrlsProperties urls) {
        this.restClient = ServiceClients.create(urls.authUrl());
    }

    public Optional<RemoteUser> findUser(UUID id) {
        return ServiceClients.call(SERVICE_NAME, () -> {
            try {
                return Optional.ofNullable(restClient.get().uri("/api/users/{id}", id)
                        .retrieve().body(RemoteUser.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
        });
    }

    /** One request per 100 ids (instead of one request per user). Unknown ids are missing from the map. */
    public Map<UUID, RemoteUser> findUsers(Collection<UUID> ids) {
        Map<UUID, RemoteUser> result = new HashMap<>();
        List<UUID> all = new ArrayList<>(ids);
        for (int from = 0; from < all.size(); from += BATCH_SIZE) {
            List<UUID> chunk = all.subList(from, Math.min(from + BATCH_SIZE, all.size()));
            RemoteUser[] users = ServiceClients.call(SERVICE_NAME, () -> restClient.get()
                    .uri(uri -> uri.path("/api/users/batch").queryParam("ids", chunk.toArray()).build())
                    .retrieve().body(RemoteUser[].class));
            if (users != null) {
                Arrays.stream(users).forEach(user -> result.put(user.id(), user));
            }
        }
        return result;
    }
}
