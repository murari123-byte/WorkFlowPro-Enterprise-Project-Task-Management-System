package com.workflowpro.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PingControllerTest {

    private final PingController controller = new PingController();

    @Test
    void pingReturnsServiceNameAndStatus() {
        Map<String, Object> response = controller.ping();

        assertThat(response.get("service")).isEqualTo("auth-service");
        assertThat(response.get("status")).isEqualTo("UP");
        assertThat(response).containsKey("timestamp");
    }
}
