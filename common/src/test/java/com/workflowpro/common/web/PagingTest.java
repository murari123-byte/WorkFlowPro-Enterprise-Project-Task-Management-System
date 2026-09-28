package com.workflowpro.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.workflowpro.common.exception.BadRequestException;

class PagingTest {

    private static final Set<String> ALLOWED = Set.of("title", "dueDate");
    private static final Sort DEFAULT = Sort.by("title");

    @Test
    void usesDefaultSortWhenNoneGiven() {
        Pageable pageable = Paging.pageable(0, 20, null, ALLOWED, DEFAULT);
        assertThat(pageable.getSort()).isEqualTo(DEFAULT);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void parsesFieldAndDirection() {
        Pageable pageable = Paging.pageable(2, 10, "dueDate,desc", ALLOWED, DEFAULT);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "dueDate"));
        assertThat(pageable.getPageNumber()).isEqualTo(2);
    }

    @Test
    void mapsApiSortNameToEntityProperty() {
        Pageable pageable = Paging.pageable(0, 20, "priority,desc", Map.of("priority", "priorityRank"), DEFAULT);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "priorityRank"));
    }

    @Test
    void rejectsUnknownSortField() {
        assertThatThrownBy(() -> Paging.pageable(0, 20, "passwordHash", ALLOWED, DEFAULT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot sort by 'passwordHash'");
    }

    @Test
    void rejectsBadDirectionAndSizes() {
        assertThatThrownBy(() -> Paging.pageable(0, 20, "title,sideways", ALLOWED, DEFAULT))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Paging.pageable(0, 101, null, ALLOWED, DEFAULT))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Paging.pageable(-1, 20, null, ALLOWED, DEFAULT))
                .isInstanceOf(BadRequestException.class);
    }
}
