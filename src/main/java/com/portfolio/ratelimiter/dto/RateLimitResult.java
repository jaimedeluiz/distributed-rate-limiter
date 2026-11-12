package com.portfolio.ratelimiter.dto;

public record RateLimitResult(
    boolean allowed,
    long remainingTokens,
    long retryAfterMs,
    long capacity
) {
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfterMs + 999) / 1000);
    }
}
