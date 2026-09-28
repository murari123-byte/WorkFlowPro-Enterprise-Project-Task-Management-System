package com.workflowpro.common.web;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.workflowpro.common.exception.BadRequestException;

/**
 * Builds a safe {@link Pageable} from request parameters.
 *
 * Sorting is limited to an explicit list of allowed fields, so clients cannot sort by
 * internal or sensitive columns, and a typo gives a clear 400 instead of a 500.
 */
public final class Paging {

    public static final int MAX_PAGE_SIZE = 100;

    private Paging() {
    }

    /**
     * @param sort "field" or "field,asc" / "field,desc"; null or blank = defaultSort
     */
    public static Pageable pageable(int page, int size, String sort, Set<String> allowedSortFields, Sort defaultSort) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, parseSort(sort, allowedSortFields, defaultSort));
    }

    private static Sort parseSort(String sort, Set<String> allowedSortFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!allowedSortFields.contains(field)) {
            throw new BadRequestException("Cannot sort by '" + field + "'. Allowed: " + String.join(", ",
                    allowedSortFields.stream().sorted().toList()));
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new BadRequestException("Sort direction must be 'asc' or 'desc'"));
        }
        return Sort.by(direction, field);
    }
}
