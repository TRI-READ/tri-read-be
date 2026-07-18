package com.triread.api.common;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size,
                              long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }

    public static int page(int requestedPage) {
        return Math.max(0, requestedPage);
    }

    public static int size(int requestedSize) {
        return Math.max(1, Math.min(50, requestedSize));
    }
}
