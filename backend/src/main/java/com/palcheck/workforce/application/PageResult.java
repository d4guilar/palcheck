package com.palcheck.workforce.application;

import java.util.List;

/** One page of results (zero-based) with the total number of matching items. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalItems + size - 1) / size);
    }
}
