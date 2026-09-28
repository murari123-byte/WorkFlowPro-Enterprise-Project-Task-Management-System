package com.workflowpro.gateway;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** A route whose service is not running must answer 503, not 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayServiceDownTest {

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void pointTaskRouteAtClosedPort(DynamicPropertyRegistry registry) {
        registry.add("TASK_SERVICE_URL", () -> "http://localhost:" + freePort());
    }

    @Test
    void unreachableServiceReturns503WithJsonError() {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .get().uri("/api/tasks/ping").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.path").isEqualTo("/api/tasks/ping");
    }

    /** A port that was free a moment ago, so nothing is listening on it. */
    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
