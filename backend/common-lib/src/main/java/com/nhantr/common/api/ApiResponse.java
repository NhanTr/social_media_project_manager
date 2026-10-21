package com.nhantr.common.api;

import java.time.Instant;

public record ApiResponse<T>(T data, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, Instant.now());
    }
}
