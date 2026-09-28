package com.workflowpro.task.controller;

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

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import com.workflowpro.common.client.RemoteUser;
import com.workflowpro.common.client.UserDirectoryClient;
import com.workflowpro.common.exception.ResourceNotFoundException;
import com.workflowpro.task.TestcontainersConfiguration;
import com.workflowpro.task.client.AccessibleProjects;
import com.workflowpro.task.client.ProjectClient;
import com.workflowpro.task.client.ProjectMembership;
import com.workflowpro.task.support.TestJwts;

/** Real HTTP, security and PostgreSQL. project-service and auth-service are mocked. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProjectClient projectClient;
    @MockitoBean private UserDirectoryClient userDirectory;

    private final UUID projectId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private Map<UUID, RemoteUser> users;

    private String manager;
    private String employee;
    private String outsider;

    @BeforeEach
    void setUp() {
        users = Map.of(
                managerId, new RemoteUser(managerId, "pm@x.com", "Pam", "Manager", List.of("PROJECT_MANAGER"), true),
                employeeId, new RemoteUser(employeeId, "emp@x.com", "Eve", "Worker", List.of("EMPLOYEE"), true));
        manager = TestJwts.bearer(managerId, "PROJECT_MANAGER");
        employee = TestJwts.bearer(employeeId, "EMPLOYEE");
        outsider = TestJwts.bearer(outsiderId, "EMPLOYEE");

        // project-service answers depend on WHO is asking, like the real service (token relay).
        // Mockito cannot see the caller, so membership is decided per test through the security context:
        ProjectMembership membership = new ProjectMembership(projectId, "Website", "ACTIVE", managerId,
                List.of(managerId, employeeId));
        when(projectClient.getMembership(any())).thenAnswer(inv -> {
            UUID caller = currentUserId();
            if (!inv.getArgument(0).equals(projectId) || !membership.isMember(caller)) {
                throw new ResourceNotFoundException("Project not found");
            }
            return membership;
        });
        when(projectClient.getAccessibleProjects()).thenAnswer(inv -> membership.isMember(currentUserId())
                ? new AccessibleProjects(false, List.of(projectId)) : new AccessibleProjects(false, List.of()));
        when(userDirectory.findUser(any())).thenAnswer(inv -> Optional.ofNullable(users.get(inv.<UUID>getArgument(0))));
        when(userDirectory.findUsers(anyCollection())).thenAnswer(inv -> inv.<Collection<UUID>>getArgument(0).stream()
                .filter(users::containsKey).collect(Collectors.toMap(Function.identity(), users::get)));
    }

    private static UUID currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }

    private String createTask(String title, String priority, String dueDate) throws Exception {
        String body = mockMvc.perform(post("/api/tasks").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"title\":\"" + title + "\",\"priority\":\"" + priority
                                + "\",\"dueDate\":" + (dueDate == null ? "null" : "\"" + dueDate + "\"") + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void fullTaskWorkflowWithHistory() throws Exception {
        String id = createTask("Build login page", "HIGH", LocalDate.now().plusDays(5).toString());

        // employee cannot assign; manager assigns to employee
        mockMvc.perform(put("/api/tasks/" + id + "/assignee").header("Authorization", employee)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + employeeId + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/tasks/" + id + "/assignee").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + employeeId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.firstName").value("Eve"));

        // non-member cannot be assigned
        mockMvc.perform(put("/api/tasks/" + id + "/assignee").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + outsiderId + "\"}"))
                .andExpect(status().isUnprocessableContent());

        // assignee: TODO -> IN_PROGRESS -> IN_REVIEW, but cannot complete
        mockMvc.perform(get("/api/tasks/" + id).header("Authorization", employee))
                .andExpect(jsonPath("$.allowedStatuses").value(contains("IN_PROGRESS")))
                .andExpect(jsonPath("$.permissions.canEdit").value(false));
        changeStatus(id, employee, "IN_PROGRESS").andExpect(status().isOk());
        changeStatus(id, employee, "IN_REVIEW").andExpect(status().isOk());
        changeStatus(id, employee, "COMPLETED").andExpect(status().isForbidden());

        // manager approves
        changeStatus(id, manager, "COMPLETED").andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").exists());
        // workflow: COMPLETED -> IN_REVIEW is not a valid move
        changeStatus(id, manager, "IN_REVIEW").andExpect(status().isUnprocessableContent());

        // timeline, newest first
        mockMvc.perform(get("/api/tasks/" + id + "/history").header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].action").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.content[0].newValue").value("COMPLETED"))
                .andExpect(jsonPath("$.content[3].action").value("ASSIGNED"))
                .andExpect(jsonPath("$.content[3].newValue").value("Eve Worker"))
                .andExpect(jsonPath("$.content[4].action").value("CREATED"));
    }

    @Test
    void editRecordsPriorityAndDueDateChanges() throws Exception {
        String id = createTask("Write docs", "LOW", null);
        String due = LocalDate.now().plusDays(3).toString();
        mockMvc.perform(put("/api/tasks/" + id).header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write API docs\",\"priority\":\"URGENT\",\"dueDate\":\"" + due + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"));
        mockMvc.perform(get("/api/tasks/" + id + "/history").header("Authorization", manager))
                .andExpect(jsonPath("$.content[*].action").value(org.hamcrest.Matchers.hasItems(
                        "PRIORITY_CHANGED", "DUE_DATE_CHANGED", "UPDATED", "CREATED")));
    }

    @Test
    void outsiderCannotSeeTasks() throws Exception {
        String id = createTask("Secret", "LOW", null);
        mockMvc.perform(get("/api/tasks/" + id).header("Authorization", outsider)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks").header("Authorization", outsider))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchFiltersSortsAndPages() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        createTask(tag + " low", "LOW", null);
        createTask(tag + " urgent", "URGENT", null);
        createTask(tag + " high", "HIGH", null);

        mockMvc.perform(get("/api/tasks?projectId=" + projectId + "&search=" + tag + "&sort=priority,desc&size=2")
                        .header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].priority").value(contains("URGENT", "HIGH")));
        mockMvc.perform(get("/api/tasks?search=" + tag + "&priority=LOW").header("Authorization", employee))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(get("/api/tasks?sort=assigneeId").header("Authorization", employee))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks?status=NOPE").header("Authorization", employee))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationAndDelete() throws Exception {
        mockMvc.perform(post("/api/tasks").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"title\":\"\",\"dueDate\":\"2000-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.dueDate").value("dueDate cannot be in the past"));
        mockMvc.perform(post("/api/tasks").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"title\":\"x\"}"))
                .andExpect(status().isForbidden());

        String id = createTask("Temp", "MEDIUM", null);
        mockMvc.perform(delete("/api/tasks/" + id).header("Authorization", employee)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tasks/" + id).header("Authorization", manager)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/" + id).header("Authorization", manager)).andExpect(status().isNotFound());
    }

    @Test
    void statsCountOverdueAndPending() throws Exception {
        long before = JsonPath.<Integer>read(mockMvc.perform(get("/api/tasks/stats?projectId=" + projectId)
                .header("Authorization", manager)).andReturn().getResponse().getContentAsString(), "$.total");
        createTask("Stats a", "LOW", null);
        createTask("Stats b", "URGENT", LocalDate.now().toString());

        mockMvc.perform(get("/api/tasks/stats?projectId=" + projectId).header("Authorization", manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value((int) before + 2))
                .andExpect(jsonPath("$.byPriority.URGENT").exists())
                .andExpect(jsonPath("$.byStatus.TODO").exists());
        mockMvc.perform(get("/api/tasks/count?projectId=" + projectId).header("Authorization", manager))
                .andExpect(jsonPath("$.count").value((int) before + 2));
    }

    @Test
    void noTokenIs401() throws Exception {
        mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(String id, String token, String status)
            throws Exception {
        return mockMvc.perform(patch("/api/tasks/" + id + "/status").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }
}
