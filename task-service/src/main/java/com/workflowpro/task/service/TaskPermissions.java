package com.workflowpro.task.service;

import java.util.List;
import java.util.Set;

import com.workflowpro.common.security.AuthenticatedUser;
import com.workflowpro.task.client.ProjectMembership;
import com.workflowpro.task.entity.Task;
import com.workflowpro.task.entity.TaskStatus;

/**
 * All "who may do what with a task" rules in one place (plain Java, easy to unit-test).
 *
 * A "lead" of a project is: an ADMIN, the project's manager, or a TEAM_LEAD who is a member.
 * <ul>
 *   <li>view: every project member (checked by project-service)</li>
 *   <li>create / edit / assign: leads</li>
 *   <li>delete: ADMIN or the project manager</li>
 *   <li>status: leads may make any allowed move. The assignee may move their own task between
 *       TODO, IN_PROGRESS and IN_REVIEW (start work, send for review) but not complete, cancel or reopen it.</li>
 * </ul>
 */
public final class TaskPermissions {

    private static final Set<TaskStatus> ASSIGNEE_STATUSES = TaskStatus.openStatuses();

    private TaskPermissions() {
    }

    public static boolean isLead(AuthenticatedUser user, ProjectMembership project) {
        return user.isAdmin()
                || project.isManager(user.id())
                || (user.hasRole(AuthenticatedUser.TEAM_LEAD) && project.isMember(user.id()));
    }

    public static boolean canEdit(AuthenticatedUser user, ProjectMembership project) {
        return isLead(user, project);
    }

    public static boolean canDelete(AuthenticatedUser user, ProjectMembership project) {
        return user.isAdmin() || project.isManager(user.id());
    }

    public static boolean canChangeStatus(AuthenticatedUser user, ProjectMembership project, Task task,
                                          TaskStatus target) {
        if (!task.getStatus().canMoveTo(target)) {
            return false;
        }
        if (isLead(user, project)) {
            return true;
        }
        boolean isAssignee = user.id().equals(task.getAssigneeId()) && project.isMember(user.id());
        return isAssignee && ASSIGNEE_STATUSES.contains(task.getStatus()) && ASSIGNEE_STATUSES.contains(target);
    }

    /** The statuses this user may move the task to right now (sorted), for the UI. */
    public static List<TaskStatus> allowedStatuses(AuthenticatedUser user, ProjectMembership project, Task task) {
        if (!project.acceptsTaskChanges()) {
            return List.of();
        }
        return task.getStatus().allowedNext().stream()
                .filter(target -> canChangeStatus(user, project, task, target))
                .sorted()
                .toList();
    }
}
