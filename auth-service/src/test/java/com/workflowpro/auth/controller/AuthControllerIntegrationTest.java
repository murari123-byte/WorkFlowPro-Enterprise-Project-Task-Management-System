package com.workflowpro.auth.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.workflowpro.auth.TestcontainersConfiguration;

/**
 * End-to-end tests of the auth endpoints against a real PostgreSQL (Testcontainers),
 * with the real security filter chain and real JWT signing/verification.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullFlowRegisterMeRefreshLogout() throws Exception {
        String email = uniqueEmail();

        // register -> 201 with tokens and EMPLOYEE role
        String registerBody = register(email, "Secret123!")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.roles").value(contains("EMPLOYEE")))
                .andExpect(jsonPath("$.user", not(hasKey("passwordHash"))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(registerBody, "$.accessToken");

        // /me with the access token
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // login (email is case-insensitive)
        String loginBody = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email.toUpperCase(), "password", "Secret123!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(loginBody, "$.refreshToken");

        // refresh -> new pair
        String refreshBody = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String rotatedRefreshToken = JsonPath.read(refreshBody, "$.refreshToken");

        // the old refresh token is single-use -> 401, and this also revokes the rotated one
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", rotatedRefreshToken)))
                .andExpect(status().isUnauthorized());

        // logout revokes a fresh session's refresh token
        String secondLogin = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", "Secret123!")))
                .andReturn().getResponse().getContentAsString();
        String sessionRefresh = JsonPath.read(secondLogin, "$.refreshToken");
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", sessionRefresh)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", sessionRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleSentByClientIsIgnored() throws Exception {
        String body = """
                {"email":"%s","password":"Secret123!","firstName":"Eve","lastName":"Hacker","roles":["ADMIN"]}
                """.formatted(uniqueEmail());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.roles").value(contains("EMPLOYEE")));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        String email = uniqueEmail();
        register(email, "Secret123!").andExpect(status().isCreated());
        register(email.toUpperCase(), "Secret123!")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    void invalidInputReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", "not-an-email", "password", "short", "firstName", "", "lastName", "X")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        String email = uniqueEmail();
        register(email, "Secret123!").andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", "WrongPass1!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void meWithTamperedTokenReturns401() throws Exception {
        String body = register(uniqueEmail(), "Secret123!").andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password, "firstName", "Jane", "lastName", "Doe")));
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** Builds a flat JSON object from key/value pairs: json("a", "1") -> {"a":"1"}. */
    private static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }
}
