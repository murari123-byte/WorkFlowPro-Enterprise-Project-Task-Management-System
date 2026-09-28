package com.workflowpro.common.client;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Service-to-service calls reuse the CALLER's access token ("token relay").
 * The called service then applies its own rules for that same user, so no service
 * gets more rights than the person who started the request.
 */
public class BearerTokenRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            request.getHeaders().setBearerAuth(jwtAuth.getToken().getTokenValue());
        }
        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            throw new IllegalStateException("No authenticated user to relay the token for " + request.getURI());
        }
        return execution.execute(request, body);
    }
}
