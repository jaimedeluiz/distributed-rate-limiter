package com.portfolio.ratelimiter.dto;

import java.time.Instant;

public record ApiResponse<T>(
    String status,
    String message,
    T data,
    Instant timestamp
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("SUCCESS", message, data, Instant.now());
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>("SUCCESS", message, null, Instant.now());
    }
}
