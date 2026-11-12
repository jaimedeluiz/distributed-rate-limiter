package com.portfolio.ratelimiter.service;

import com.portfolio.ratelimiter.config.RateLimiterProperties;
import com.portfolio.ratelimiter.dto.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisScript<List> tokenBucketScript;

    private RateLimiterProperties properties;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(10);
        properties.setDefaultRefillRate(2.0);
        rateLimiterService = new RateLimiterService(redisTemplate, tokenBucketScript, properties);
    }

    @Test
    @DisplayName("Should allow request when tokens are available")
    void testTryAcquire_Allowed() {
        // Mock Redis response: [1, 9, 0, 10] (allowed=1, remaining=9, retry_after=0, capacity=10)
        when(redisTemplate.execute(eq(tokenBucketScript), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(1L, 9L, 0L, 10L));

        RateLimitResult result = rateLimiterService.tryAcquire("test-client", 10, 2.0);

        assertTrue(result.allowed());
        assertEquals(9, result.remainingTokens());
        assertEquals(0, result.retryAfterMs());
        assertEquals(10, result.capacity());
    }

    @Test
    @DisplayName("Should block request when token bucket is depleted")
    void testTryAcquire_Blocked() {
        // Mock Redis response: [0, 0, 500, 10] (allowed=0, remaining=0, retry_after=500ms, capacity=10)
        when(redisTemplate.execute(eq(tokenBucketScript), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(0L, 0L, 500L, 10L));

        RateLimitResult result = rateLimiterService.tryAcquire("test-client", 10, 2.0);

        assertFalse(result.allowed());
        assertEquals(0, result.remainingTokens());
        assertEquals(500, result.retryAfterMs());
        assertEquals(1, result.retryAfterSeconds());
        assertEquals(10, result.capacity());
    }

    @Test
    @DisplayName("Should fail open gracefully when Redis throws an exception")
    void testTryAcquire_FailOpenOnRedisFailure() {
        when(redisTemplate.execute(eq(tokenBucketScript), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Redis connection timed out"));

        RateLimitResult result = rateLimiterService.tryAcquire("test-client", 10, 2.0);

        // Fail-open strategy permits the request
        assertTrue(result.allowed());
        assertEquals(10, result.capacity());
    }
}
