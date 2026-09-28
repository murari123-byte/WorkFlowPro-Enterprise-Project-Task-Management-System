package com.workflowpro.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.workflowpro.common.exception.BusinessRuleException;
import com.workflowpro.common.exception.ConflictException;
import com.workflowpro.common.exception.ForbiddenException;
import com.workflowpro.common.exception.ResourceNotFoundException;
import com.workflowpro.common.security.AuthenticatedUser;
import com.workflowpro.project.client.RemoteUser;
import com.workflowpro.project.client.TaskClient;
import com.workflowpro.project.client.UserClient;
import com.workflowpro.project.dto.ProjectRequest;
import com.workflowpro.project.entity.Project;
import com.workflowpro.project.entity.ProjectStatus;
import com.workflowpro.project.repository.ProjectRepository;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private UserClient userClient;
    @Mock private TaskClient taskClient;

    private ProjectService service;

    private final UUID pmId = UUID.randomUUID();
    private final AuthenticatedUser pm = new AuthenticatedUser(pmId, "pm@x.com", List.of("PROJECT_MANAGER"));
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "a@x.com", List.of("ADMIN"));
    private final AuthenticatedUser employee = new AuthenticatedUser(UUID.randomUUID(), "e@x.com", List.of("EMPLOYEE"));

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepository, userClient, taskClient);
    }

    private RemoteUser remote(UUID id, String... roles) {
        return new RemoteUser(id, id + "@x.com", "First", "Last", List.of(roles), true);
    }

    private Project projectManagedBy(UUID managerId) {
        return new Project("Website", null, null, null, managerId, managerId);
    }

    @Test
    void projectManagerCreatesProjectAndBecomesManagerAndMember() {
        when(projectRepository.existsByNameIgnoreCase("Website")).thenReturn(false);
        when(userClient.findUser(pmId)).thenReturn(Optional.of(remote(pmId, "PROJECT_MANAGER")));
        when(userClient.findUsers(any())).thenReturn(Map.of());

        var response = service.create(pm, new ProjectRequest(" Website ", "", LocalDate.now(), null, null));

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Website");
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(saved.getValue().getManagerId()).isEqualTo(pmId);
        assertThat(saved.getValue().isMember(pmId)).isTrue();
        assertThat(saved.getValue().getStatus()).isEqualTo(ProjectStatus.PLANNING);
        assertThat(response.canManage()).isTrue();
    }

    @Test
    void projectManagerCannotCreateProjectForSomeoneElse() {
        assertThatThrownBy(() -> service.create(pm, new ProjectRequest("X", null, null, null, UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void managerMustHaveManagerOrAdminRole() {
        UUID employeeId = UUID.randomUUID();
        when(projectRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(userClient.findUser(employeeId)).thenReturn(Optional.of(remote(employeeId, "EMPLOYEE")));

        assertThatThrownBy(() -> service.create(admin, new ProjectRequest("X", null, null, null, employeeId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PROJECT_MANAGER or ADMIN");
    }

    @Test
    void duplicateNameIsRejected() {
        when(projectRepository.existsByNameIgnoreCase("Website")).thenReturn(true);

        assertThatThrownBy(() -> service.create(pm, new ProjectRequest("Website", null, null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void nonMemberGets404NotForbidden() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.of(projectManagedBy(pmId)));

        assertThatThrownBy(() -> service.get(employee, id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void memberWhoIsNotManagerCannotEdit() {
        UUID id = UUID.randomUUID();
        Project project = projectManagedBy(pmId);
        project.addMember(employee.id());
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.update(employee, id, new ProjectRequest("New", null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void invalidStatusChangeIsRejected() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.of(projectManagedBy(pmId)));

        assertThatThrownBy(() -> service.changeStatus(pm, id, ProjectStatus.COMPLETED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PLANNING to COMPLETED");
    }

    @Test
    void closedProjectIsReadOnly() {
        UUID id = UUID.randomUUID();
        Project project = projectManagedBy(pmId);
        project.changeStatus(ProjectStatus.CANCELLED);
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.addMember(pm, id, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void managerCannotBeRemovedFromMembers() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.of(projectManagedBy(pmId)));

        assertThatThrownBy(() -> service.removeMember(pm, id, pmId)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void projectWithTasksCannotBeDeleted() {
        UUID id = UUID.randomUUID();
        Project project = projectManagedBy(pmId);
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));
        when(taskClient.countTasks(id)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(pm, id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3 task(s)");
        verify(projectRepository, never()).delete(any(Project.class));
    }

    @Test
    void adminSeesEveryProjectInAccessibleList() {
        assertThat(service.accessibleProjects(admin).allProjects()).isTrue();
    }
}
