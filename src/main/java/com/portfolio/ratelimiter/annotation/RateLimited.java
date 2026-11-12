package com.portfolio.ratelimiter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enforce Distributed Token Bucket rate limiting on specific endpoints or controllers.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    /**
     * Maximum capacity of the token bucket.
     * Default: -1 (falls back to global default-capacity).
     */
    long capacity() default -1;

    /**
     * Refill rate in tokens per second.
     * Default: -1.0 (falls back to global default-refill-rate).
     */
    double refillRate() default -1.0;

    /**
     * Optional custom key prefix for namespacing rate limits.
     */
    String keyPrefix() default "";
}
