package com.workflowpro.task.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.workflowpro.common.security.AuthenticatedUser;
import com.workflowpro.task.client.ProjectMembership;
import com.workflowpro.task.entity.Task;
import com.workflowpro.task.entity.TaskPriority;
import com.workflowpro.task.entity.TaskStatus;

class TaskPermissionsTest {

    private final UUID managerId = UUID.randomUUID();
    private final UUID leadId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID outsiderLeadId = UUID.randomUUID();
    private final ProjectMembership project = new ProjectMembership(UUID.randomUUID(), "P", "ACTIVE", managerId,
            List.of(managerId, leadId, employeeId));

    private final AuthenticatedUser manager = user(managerId, "PROJECT_MANAGER");
    private final AuthenticatedUser lead = user(leadId, "TEAM_LEAD");
    private final AuthenticatedUser employee = user(employeeId, "EMPLOYEE");
    private final AuthenticatedUser outsiderLead = user(outsiderLeadId, "TEAM_LEAD");
    private final AuthenticatedUser admin = user(UUID.randomUUID(), "ADMIN");

    private static AuthenticatedUser user(UUID id, String role) {
        return new AuthenticatedUser(id, id + "@x.com", List.of(role));
    }

    private Task taskAssignedTo(UUID assignee, TaskStatus status) {
        Task task = new Task(project.projectId(), "T", null, TaskPriority.MEDIUM, LocalDate.now(), managerId);
        task.setAssigneeId(assignee);
        task.changeStatus(status, null);
        return task;
    }

    @Test
    void leadsAreAdminManagerAndTeamLeadMembersOnly() {
        assertThat(TaskPermissions.isLead(admin, project)).isTrue();
        assertThat(TaskPermissions.isLead(manager, project)).isTrue();
        assertThat(TaskPermissions.isLead(lead, project)).isTrue();
        assertThat(TaskPermissions.isLead(employee, project)).isFalse();
        assertThat(TaskPermissions.isLead(outsiderLead, project)).as("TEAM_LEAD role alone is not enough").isFalse();
    }

    @Test
    void onlyManagerOrAdminCanDelete() {
        assertThat(TaskPermissions.canDelete(manager, project)).isTrue();
        assertThat(TaskPermissions.canDelete(admin, project)).isTrue();
        assertThat(TaskPermissions.canDelete(lead, project)).isFalse();
    }

    @Test
    void assigneeCanStartAndSubmitButNotComplete() {
        Task inProgress = taskAssignedTo(employeeId, TaskStatus.IN_PROGRESS);
        assertThat(TaskPermissions.allowedStatuses(employee, project, inProgress))
                .containsExactly(TaskStatus.TODO, TaskStatus.IN_REVIEW);

        Task inReview = taskAssignedTo(employeeId, TaskStatus.IN_REVIEW);
        assertThat(TaskPermissions.canChangeStatus(employee, project, inReview, TaskStatus.COMPLETED)).isFalse();
        assertThat(TaskPermissions.canChangeStatus(lead, project, inReview, TaskStatus.COMPLETED)).isTrue();
    }

    @Test
    void nonAssigneeEmployeeCannotChangeStatus() {
        Task task = taskAssignedTo(managerId, TaskStatus.TODO);
        assertThat(TaskPermissions.allowedStatuses(employee, project, task)).isEmpty();
    }

    @Test
    void workflowStillApplies() {
        Task todo = taskAssignedTo(null, TaskStatus.TODO);
        assertThat(TaskPermissions.canChangeStatus(admin, project, todo, TaskStatus.COMPLETED)).isFalse();
    }

    @Test
    void nothingCanChangeWhileProjectIsOnHold() {
        ProjectMembership onHold = new ProjectMembership(project.projectId(), "P", "ON_HOLD", managerId, project.memberIds());
        assertThat(TaskPermissions.allowedStatuses(manager, onHold, taskAssignedTo(null, TaskStatus.TODO))).isEmpty();
    }

    @Test
    void overdueMeansPastDueAndStillOpen() {
        LocalDate today = LocalDate.of(2026, 10, 10);
        Task task = new Task(project.projectId(), "T", null, TaskPriority.LOW, today.minusDays(1), managerId);
        assertThat(task.isOverdue(today)).isTrue();
        task.changeStatus(TaskStatus.COMPLETED, null);
        assertThat(task.isOverdue(today)).isFalse();
    }
}
