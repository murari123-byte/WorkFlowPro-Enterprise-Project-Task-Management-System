package com.workflowpro.common.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Calls auth-service's /api/users endpoints with the caller's token.
 * Each service creates one as a bean with its AUTH_SERVICE_URL.
 */
public class UserDirectoryClient {

    private static final String SERVICE_NAME = "auth-service";
    private static final int BATCH_SIZE = 100;

    private final RestClient restClient;

    public UserDirectoryClient(String authServiceUrl) {
        this.restClient = ServiceClients.create(authServiceUrl);
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

    /** Summary for an id from a lookup map, or an "Unknown user" placeholder. */
    public static UserSummary summaryOf(UUID userId, Map<UUID, RemoteUser> users) {
        RemoteUser user = users.get(userId);
        return user == null ? UserSummary.unknown(userId) : user.toSummary();
    }
}
