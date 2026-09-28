package com.workflowpro.common.client;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.workflowpro.common.exception.ServiceUnavailableException;

/**
 * Creates the RestClient used for service-to-service calls and maps network problems to 503.
 *
 * Every client: short timeouts (a slow service must not hang the caller), JSON,
 * and the caller's Bearer token relayed ({@link BearerTokenRelayInterceptor}).
 */
public final class ServiceClients {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private ServiceClients() {
    }

    public static RestClient create(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new BearerTokenRelayInterceptor())
                .build();
    }

    /**
     * Runs a call and turns "service down / timed out / 5xx" into a 503 for our own client.
     * 4xx errors (404, 403, ...) are left for the caller to handle.
     */
    public static <T> T call(String serviceName, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ServiceUnavailableException(serviceName + " is not available. Please try again later");
        }
    }
}
