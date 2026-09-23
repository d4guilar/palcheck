package com.palcheck.integrations.application;

import java.util.List;

/** A page of results. Mirrors {@code workforce.application.PageResult}; not shared across modules on purpose. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalItems + size - 1) / size);
    }
}
