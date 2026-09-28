package com.workflowpro.common.client;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.workflowpro.common.exception.ForbiddenException;
import com.workflowpro.common.exception.ServiceUnavailableException;
import com.workflowpro.common.exception.UnauthorizedException;

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
     * Runs a call and maps the other service's problems to a clear status for OUR client:
     * service down / timed out / 5xx -> 503, token rejected -> 401, not allowed -> 403.
     * Other 4xx errors (e.g. 404) are left for the caller to handle.
     */
    public static <T> T call(String serviceName, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ServiceUnavailableException(serviceName + " is not available. Please try again later");
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new UnauthorizedException("Your session has expired. Please sign in again");
        } catch (HttpClientErrorException.Forbidden e) {
            throw new ForbiddenException("You do not have permission to perform this action");
        }
    }
}
