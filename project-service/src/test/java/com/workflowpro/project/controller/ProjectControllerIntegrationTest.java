package com.workflowpro.project.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.workflowpro.project.TestcontainersConfiguration;
import com.workflowpro.project.client.RemoteUser;
import com.workflowpro.project.client.TaskClient;
import com.workflowpro.project.client.UserClient;
import com.workflowpro.project.support.TestJwts;

/**
 * Real HTTP layer, real security, real PostgreSQL (Testcontainers).
 * Only the calls to auth-service and task-service are replaced by mocks.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserClient userClient;
    @MockitoBean private TaskClient taskClient;

    /** Fake auth-service directory: user id -> user. */
    private final Map<UUID, RemoteUser> directory = new ConcurrentHashMap<>();

    private UUID pm;
    private UUID otherPm;
    private UUID employee;
    private UUID admin;

    @BeforeEach
    void setUp() {
        pm = addUser("PROJECT_MANAGER");
        otherPm = addUser("PROJECT_MANAGER");
        employee = addUser("EMPLOYEE");
        admin = addUser("ADMIN");
        when(userClient.findUser(any())).thenAnswer(inv -> Optional.ofNullable(directory.get(inv.<UUID>getArgument(0))));
        when(userClient.findUsers(anyCollection())).thenAnswer(inv -> inv.<Collection<UUID>>getArgument(0).stream()
                .filter(directory::containsKey)
                .collect(Collectors.toMap(Function.identity(), directory::get)));
    }

    private UUID addUser(String role) {
        UUID id = UUID.randomUUID();
        directory.put(id, new RemoteUser(id, id + "@x.com", role.toLowerCase(), "User", List.of(role), true));
        return id;
    }

    private String createProject(UUID managerUserId, String role) throws Exception {
        String body = mockMvc.perform(post("/api/projects").header("Authorization", TestJwts.bearer(managerUserId, role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project " + UUID.randomUUID() + "\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-12-31\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotCreateProject() throws Exception {
        mockMvc.perform(post("/api/projects").header("Authorization", TestJwts.bearer(employee, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createValidatesInput() throws Exception {
        mockMvc.perform(post("/api/projects").header("Authorization", TestJwts.bearer(pm, "PROJECT_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"startDate\":\"2026-12-01\",\"endDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.dateRangeValid").value("endDate must not be before startDate"));
    }

    @Test
    void fullProjectWorkflow() throws Exception {
        String id = createProject(pm, "PROJECT_MANAGER");
        String pmToken = TestJwts.bearer(pm, "PROJECT_MANAGER");
        String employeeToken = TestJwts.bearer(employee, "EMPLOYEE");

        // employee is not a member yet -> 404
        mockMvc.perform(get("/api/projects/" + id).header("Authorization", employeeToken))
                .andExpect(status().isNotFound());

        // manager adds the employee
        mockMvc.perform(post("/api/projects/" + id + "/members").header("Authorization", pmToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + employee + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members", hasSize(2)));

        // now the employee can see it, but cannot manage it
        mockMvc.perform(get("/api/projects/" + id).header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(false))
                .andExpect(jsonPath("$.allowedStatuses", hasSize(0)));
        mockMvc.perform(patch("/api/projects/" + id + "/status").header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());

        // PLANNING -> COMPLETED is not allowed, PLANNING -> ACTIVE is
        mockMvc.perform(patch("/api/projects/" + id + "/status").header("Authorization", pmToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(patch("/api/projects/" + id + "/status").header("Authorization", pmToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.allowedStatuses").value(contains("ON_HOLD", "COMPLETED", "CANCELLED")));

        // the membership view used by task-service
        mockMvc.perform(get("/api/projects/" + id + "/membership").header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(pm.toString()))
                .andExpect(jsonPath("$.memberIds", hasSize(2)));

        // the manager cannot be removed; the employee can
        mockMvc.perform(delete("/api/projects/" + id + "/members/" + pm).header("Authorization", pmToken))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(delete("/api/projects/" + id + "/members/" + employee).header("Authorization", pmToken))
                .andExpect(status().isNoContent());

        // delete is blocked while there are tasks
        when(taskClient.countTasks(UUID.fromString(id))).thenReturn(2L);
        mockMvc.perform(delete("/api/projects/" + id).header("Authorization", pmToken))
                .andExpect(status().isConflict());
        when(taskClient.countTasks(UUID.fromString(id))).thenReturn(0L);
        mockMvc.perform(delete("/api/projects/" + id).header("Authorization", pmToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/projects/" + id).header("Authorization", pmToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminCanReassignManager() throws Exception {
        String id = createProject(pm, "PROJECT_MANAGER");
        String body = "{\"userId\":\"" + otherPm + "\"}";

        mockMvc.perform(put("/api/projects/" + id + "/manager").header("Authorization", TestJwts.bearer(pm, "PROJECT_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/projects/" + id + "/manager").header("Authorization", TestJwts.bearer(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manager.id").value(otherPm.toString()));
    }

    @Test
    void listShowsOnlyMyProjectsWithPagingAndFilters() throws Exception {
        UUID lonelyPm = addUser("PROJECT_MANAGER");
        createProject(lonelyPm, "PROJECT_MANAGER");
        createProject(lonelyPm, "PROJECT_MANAGER");
        createProject(pm, "PROJECT_MANAGER");

        mockMvc.perform(get("/api/projects?size=1").header("Authorization", TestJwts.bearer(lonelyPm, "PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].manager.id").value(lonelyPm.toString()));
        mockMvc.perform(get("/api/projects?status=ACTIVE").header("Authorization", TestJwts.bearer(lonelyPm, "PROJECT_MANAGER")))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/projects?sort=managerId").header("Authorization", TestJwts.bearer(lonelyPm, "PROJECT_MANAGER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsCountOnlyVisibleProjects() throws Exception {
        UUID freshPm = addUser("PROJECT_MANAGER");
        String id = createProject(freshPm, "PROJECT_MANAGER");
        createProject(freshPm, "PROJECT_MANAGER");
        mockMvc.perform(patch("/api/projects/" + id + "/status").header("Authorization", TestJwts.bearer(freshPm, "PROJECT_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/stats").header("Authorization", TestJwts.bearer(freshPm, "PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.byStatus.PLANNING").value(1))
                .andExpect(jsonPath("$.byStatus.CANCELLED").value(0));
    }

    @Test
    void duplicateNameIgnoringCaseIs409() throws Exception {
        String token = TestJwts.bearer(pm, "PROJECT_MANAGER");
        String name = "Dup " + UUID.randomUUID();
        mockMvc.perform(post("/api/projects").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name.toUpperCase() + "\"}")).andExpect(status().isConflict());
    }
}
