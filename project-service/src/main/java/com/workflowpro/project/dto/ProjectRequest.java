package com.workflowpro.project.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create / update a project.
 *
 * @param managerId optional on create; only an ADMIN may choose someone else. Ignored on update
 *                  (use PUT /{id}/manager).
 */
public record ProjectRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 2000) String description,
        LocalDate startDate,
        LocalDate endDate,
        UUID managerId) {

    @AssertTrue(message = "endDate must not be before startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
