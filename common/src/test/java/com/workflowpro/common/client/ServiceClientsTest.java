package com.workflowpro.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.workflowpro.common.exception.ForbiddenException;
import com.workflowpro.common.exception.ServiceUnavailableException;
import com.workflowpro.common.exception.UnauthorizedException;

class ServiceClientsTest {

    @Test
    void returnsTheResultWhenTheCallWorks() {
        assertThat(ServiceClients.call("x", () -> "ok")).isEqualTo("ok");
    }

    @Test
    void serviceDownOr5xxBecomes503() {
        assertThatThrownBy(() -> ServiceClients.call("auth-service", () -> {
            throw new ResourceAccessException("Connection refused");
        })).isInstanceOf(ServiceUnavailableException.class).hasMessageContaining("auth-service");
        assertThatThrownBy(() -> ServiceClients.call("x", () -> {
            throw HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "bad", null, null, null);
        })).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void rejectedTokenBecomes401AndForbiddenBecomes403() {
        assertThatThrownBy(() -> ServiceClients.call("x", () -> {
            throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "no", null, null, null);
        })).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> ServiceClients.call("x", () -> {
            throw HttpClientErrorException.create(HttpStatus.FORBIDDEN, "no", null, null, null);
        })).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void otherClientErrorsAreLeftForTheCaller() {
        assertThatThrownBy(() -> ServiceClients.call("x", () -> {
            throw HttpClientErrorException.create(HttpStatus.NOT_FOUND, "no", null, null, null);
        })).isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
