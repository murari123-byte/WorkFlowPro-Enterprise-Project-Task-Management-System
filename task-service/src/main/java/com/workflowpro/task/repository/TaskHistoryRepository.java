package com.workflowpro.task.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.workflowpro.task.entity.TaskHistory;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {

    Page<TaskHistory> findByTaskIdOrderByCreatedAtDescIdDesc(UUID taskId, Pageable pageable);
}
