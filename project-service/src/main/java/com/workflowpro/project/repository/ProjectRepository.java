package com.workflowpro.project.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.workflowpro.project.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /** Ids of all projects the user belongs to (the manager is always a member). */
    @Query("select p.id from Project p join p.members m where m.userId = :userId")
    List<UUID> findIdsByMember(@Param("userId") UUID userId);

    /** [status, count] rows for every project - used by the ADMIN dashboard. */
    @Query("select p.status, count(p) from Project p group by p.status")
    List<Object[]> countByStatus();

    /** [status, count] rows for the user's projects. */
    @Query("select p.status, count(p) from Project p join p.members m where m.userId = :userId group by p.status")
    List<Object[]> countByStatusForMember(@Param("userId") UUID userId);
}
