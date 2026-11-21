package com.nhantr.common.api;

import java.util.List;

public record PageResponse<T>(List<T> items, String nextCursor, boolean hasNext) {
    public static <T> PageResponse<T> of(List<T> items) {
        return new PageResponse<>(items, null, false);
    }
}
