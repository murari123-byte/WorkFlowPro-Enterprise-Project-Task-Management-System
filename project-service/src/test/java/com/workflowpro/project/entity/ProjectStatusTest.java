package com.workflowpro.project.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProjectStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PLANNING, ACTIVE, true",
            "PLANNING, CANCELLED, true",
            "PLANNING, COMPLETED, false",
            "ACTIVE, ON_HOLD, true",
            "ACTIVE, COMPLETED, true",
            "ON_HOLD, ACTIVE, true",
            "ON_HOLD, COMPLETED, false",
            "COMPLETED, ACTIVE, false",
            "CANCELLED, PLANNING, false"
    })
    void followsTheWorkflow(ProjectStatus from, ProjectStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void onlyCompletedAndCancelledAreClosed() {
        assertThat(ProjectStatus.COMPLETED.isClosed()).isTrue();
        assertThat(ProjectStatus.CANCELLED.isClosed()).isTrue();
        assertThat(ProjectStatus.ACTIVE.isClosed()).isFalse();
    }
}
