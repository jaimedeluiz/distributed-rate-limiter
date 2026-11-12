package com.portfolio.ratelimiter.dto;

import java.time.Instant;

public record RateLimitErrorResponse(
    int status,
    String error,
    String message,
    long limit,
    long remaining,
    long retryAfterSeconds,
    String clientIdentifier,
    Instant timestamp
) {
    public static RateLimitErrorResponse of(long limit, long remaining, long retryAfterSeconds, String clientIdentifier) {
        return new RateLimitErrorResponse(
            429,
            "Too Many Requests",
            "Rate limit exceeded. Bucket capacity is " + limit + " tokens. Please retry in " + retryAfterSeconds + " second(s).",
            limit,
            remaining,
            retryAfterSeconds,
            clientIdentifier,
            Instant.now()
        );
    }
}
