package com.workflowpro.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Starts the real gateway on a random port, pointing every route at a tiny fake backend
 * (JDK HttpServer) that echoes back the path and Authorization header it received.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final HttpServer FAKE_BACKEND = startFakeBackend();

    @Value("${local.server.port}")
    private int port;

    private WebTestClient client;

    @DynamicPropertySource
    static void pointRoutesAtFakeBackend(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + FAKE_BACKEND.getAddress().getPort();
        registry.add("AUTH_SERVICE_URL", () -> url);
        registry.add("PROJECT_SERVICE_URL", () -> url);
        registry.add("TASK_SERVICE_URL", () -> url);
        registry.add("NOTIFICATION_SERVICE_URL", () -> url);
        registry.add("CORS_ALLOWED_ORIGINS", () -> ALLOWED_ORIGIN);
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterAll
    static void stopFakeBackend() {
        FAKE_BACKEND.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/ping", "/api/projects/ping", "/api/tasks/ping", "/api/notifications/ping"})
    void forwardsEachServicePathUnchanged(String path) {
        client.get().uri(path).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo(path);
    }

    @Test
    void forwardsAuthorizationHeader() {
        client.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.authorization").isEqualTo("Bearer abc.def.ghi");
    }

    @Test
    void unknownPathReturns404() {
        client.get().uri("/api/unknown/ping").exchange().expectStatus().isNotFound();
    }

    @Test
    void corsPreflightFromReactDevServerIsAllowed() {
        client.options().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    @Test
    void corsPreflightFromOtherOriginIsRejected() {
        client.options().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void simpleRequestFromAllowedOriginGetsCorsHeader() {
        client.get().uri("/api/auth/ping").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    private static HttpServer startFakeBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", GatewayRoutingTest::echo);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start fake backend", e);
        }
    }

    private static void echo(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String json = "{\"path\":\"%s\",\"authorization\":%s}".formatted(
                exchange.getRequestURI().getPath(), auth == null ? "null" : "\"" + auth + "\"");
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
