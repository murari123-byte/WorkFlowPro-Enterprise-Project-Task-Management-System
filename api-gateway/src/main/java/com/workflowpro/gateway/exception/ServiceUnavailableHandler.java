package com.workflowpro.gateway.exception;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * When a backend service is not running, the gateway cannot connect to it.
 * Instead of a generic 500, return 503 Service Unavailable in the same JSON shape
 * the services use. Every other error is passed on to Spring's default handler.
 */
@Component
@Order(-2) // run before Spring Boot's default error handler (order -1)
public class ServiceUnavailableHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ServiceUnavailableHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!isConnectionFailure(ex)) {
            return Mono.error(ex);
        }
        String path = exchange.getRequest().getPath().value();
        log.warn("Backend service unreachable for {}: {}", path, ex.getMessage());

        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        String json = """
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":"%s"}"""
                .formatted(Instant.now(), status.value(), status.getReasonPhrase(),
                        "The service handling this request is not available. Please try again later.", path);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /** Netty wraps the ConnectException, so look through the whole cause chain. */
    private static boolean isConnectionFailure(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException) {
                return true;
            }
        }
        return false;
    }
}
