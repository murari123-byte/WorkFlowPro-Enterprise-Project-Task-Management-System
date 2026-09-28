package com.workflowpro.task.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.workflowpro.task.entity.Task;
import com.workflowpro.task.entity.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    long countByProjectId(UUID projectId);

    // ---- dashboard numbers: one GROUP BY query each, no rows loaded into memory ----

    @Query("select t.status, count(t) from Task t group by t.status")
    List<Object[]> countByStatus();

    @Query("select t.status, count(t) from Task t where t.projectId in :projectIds group by t.status")
    List<Object[]> countByStatusInProjects(@Param("projectIds") Collection<UUID> projectIds);

    @Query("select t.priority, count(t) from Task t group by t.priority")
    List<Object[]> countByPriority();

    @Query("select t.priority, count(t) from Task t where t.projectId in :projectIds group by t.priority")
    List<Object[]> countByPriorityInProjects(@Param("projectIds") Collection<UUID> projectIds);

    @Query("select count(t) from Task t where t.dueDate < :today and t.status in :openStatuses")
    long countOverdue(@Param("today") LocalDate today, @Param("openStatuses") Collection<TaskStatus> openStatuses);

    @Query("select count(t) from Task t where t.projectId in :projectIds and t.dueDate < :today and t.status in :openStatuses")
    long countOverdueInProjects(@Param("projectIds") Collection<UUID> projectIds, @Param("today") LocalDate today,
                                @Param("openStatuses") Collection<TaskStatus> openStatuses);

    @Query("select count(t) from Task t where t.assigneeId = :userId and t.status in :openStatuses")
    long countOpenAssignedTo(@Param("userId") UUID userId, @Param("openStatuses") Collection<TaskStatus> openStatuses);
}
