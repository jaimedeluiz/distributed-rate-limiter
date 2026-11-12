package com.portfolio.ratelimiter.service;

import com.portfolio.ratelimiter.config.RateLimiterProperties;
import com.portfolio.ratelimiter.dto.RateLimitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String KEY_PREFIX = "rate_limit:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> tokenBucketScript;
    private final RateLimiterProperties properties;

    public RateLimiterService(
            StringRedisTemplate redisTemplate,
            RedisScript<List> tokenBucketScript,
            RateLimiterProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
        this.properties = properties;
    }

    /**
     * Attempts to acquire a single token for the given client identifier.
     *
     * @param clientIdentifier IP address or API key
     * @param customCapacity   bucket capacity (if <= 0, uses configuration default)
     * @param customRefillRate refill rate in tokens/sec (if <= 0.0, uses configuration default)
     * @return RateLimitResult indicating whether the request is allowed, remaining tokens, and retry-after.
     */
    public RateLimitResult tryAcquire(String clientIdentifier, long customCapacity, double customRefillRate) {
        if (!properties.isEnabled()) {
            return new RateLimitResult(true, properties.getDefaultCapacity(), 0, properties.getDefaultCapacity());
        }

        long capacity = (customCapacity > 0) ? customCapacity : properties.getDefaultCapacity();
        double refillRate = (customRefillRate > 0) ? customRefillRate : properties.getDefaultRefillRate();
        String redisKey = KEY_PREFIX + clientIdentifier;
        long nowMillis = System.currentTimeMillis();

        try {
            List<?> result = redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    "1", // 1 token requested
                    String.valueOf(nowMillis)
            );

            if (result == null || result.size() < 4) {
                log.warn("Unexpected Redis response structure: {}. Defaulting to fail-open.", result);
                return new RateLimitResult(true, capacity - 1, 0, capacity);
            }

            long allowedNum = ((Number) result.get(0)).longValue();
            long remainingTokens = ((Number) result.get(1)).longValue();
            long retryAfterMs = ((Number) result.get(2)).longValue();
            long reportedCapacity = ((Number) result.get(3)).longValue();

            return new RateLimitResult(
                    allowedNum == 1,
                    remainingTokens,
                    retryAfterMs,
                    reportedCapacity
            );

        } catch (Exception e) {
            // Fail-open strategy: In high-scale production systems, if Redis has an outage,
            // we log the incident and gracefully permit user traffic rather than taking down the API.
            log.error("Redis rate-limiter communication error for client {}: {}. Failing open.", clientIdentifier, e.getMessage());
            return new RateLimitResult(true, capacity, 0, capacity);
        }
    }

    /**
     * Resets rate limit for a client (useful for testing and admin operations).
     */
    public boolean resetLimit(String clientIdentifier) {
        String redisKey = KEY_PREFIX + clientIdentifier;
        Boolean deleted = redisTemplate.delete(redisKey);
        return Boolean.TRUE.equals(deleted);
    }
}
