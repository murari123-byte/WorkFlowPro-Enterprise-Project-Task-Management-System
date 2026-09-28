package com.workflowpro.auth.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.workflowpro.auth.TestcontainersConfiguration;

/** RBAC on /api/users with real tokens. The ADMIN comes from the bootstrap settings in application-test.yml. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void employeeCannotListOrAdministerUsers() throws Exception {
        Session employee = register();

        mockMvc.perform(get("/api/users").header("Authorization", employee.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(put("/api/users/" + employee.id() + "/roles").header("Authorization", employee.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminChangesRolesAndTheNextTokenCarriesThem() throws Exception {
        Session admin = login("admin@test.local", "AdminPass123!");
        Session employee = register();

        mockMvc.perform(put("/api/users/" + employee.id() + "/roles").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"PROJECT_MANAGER\",\"EMPLOYEE\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(containsInAnyOrder("EMPLOYEE", "PROJECT_MANAGER")));

        // refresh -> new access token has the new role -> user can now search the directory
        String refreshed = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + employee.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles").value(containsInAnyOrder("EMPLOYEE", "PROJECT_MANAGER")))
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(refreshed, "$.accessToken");
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminSearchIsPagedAndRejectsUnknownSortField() throws Exception {
        Session admin = login("admin@test.local", "AdminPass123!");
        register();

        mockMvc.perform(get("/api/users?size=1&sort=email,desc").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/users?sort=passwordHash").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/users?search=admin@test&role=ADMIN").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("admin@test.local"));
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        Session admin = login("admin@test.local", "AdminPass123!");
        Session employee = register();

        mockMvc.perform(put("/api/users/" + employee.id() + "/status").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + employee.email() + "\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + employee.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userUpdatesProfileAndChangesPassword() throws Exception {
        Session user = register();

        mockMvc.perform(put("/api/users/me").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Janet\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Janet"));
        mockMvc.perform(put("/api/users/me/password").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/users/me/password").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Secret123!\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isNoContent());
        login(user.email(), "NewPass123!");
        // old refresh token was revoked by the password change
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + user.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyUserCanLookUpUsersByIdForOtherServices() throws Exception {
        Session a = register();
        Session b = register();

        mockMvc.perform(get("/api/users/" + b.id()).header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(b.email()));
        mockMvc.perform(get("/api/users/batch?ids=" + a.id() + "," + b.id() + "," + UUID.randomUUID())
                        .header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/users/" + UUID.randomUUID()).header("Authorization", a.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/not-a-uuid").header("Authorization", a.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bootstrapAdminExistsWithAdminRole() throws Exception {
        Session admin = login("admin@test.local", "AdminPass123!");
        mockMvc.perform(get("/api/users/me").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(contains("ADMIN")));
    }

    // ---------- helpers ----------

    private record Session(String id, String email, String accessToken, String refreshToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private Session register() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"firstName\":\"Jane\",\"lastName\":\"Doe\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return toSession(body, email);
    }

    private Session login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return toSession(body, email);
    }

    private static Session toSession(String body, String email) {
        return new Session(JsonPath.read(body, "$.user.id"), email, JsonPath.read(body, "$.accessToken"),
                JsonPath.read(body, "$.refreshToken"));
    }
}
