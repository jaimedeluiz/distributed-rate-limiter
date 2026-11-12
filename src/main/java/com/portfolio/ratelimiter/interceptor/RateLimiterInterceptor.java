package com.portfolio.ratelimiter.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.ratelimiter.annotation.RateLimited;
import com.portfolio.ratelimiter.config.RateLimiterProperties;
import com.portfolio.ratelimiter.dto.RateLimitErrorResponse;
import com.portfolio.ratelimiter.dto.RateLimitResult;
import com.portfolio.ratelimiter.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class RateLimiterInterceptor implements HandlerInterceptor {

    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RETRY_AFTER = "X-RateLimit-Retry-After-Seconds";

    private final RateLimiterService rateLimiterService;
    private final RateLimiterProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper;

    public RateLimiterInterceptor(RateLimiterService rateLimiterService, RateLimiterProperties properties) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isEnabled()) {
            return true;
        }

        // Determine if this endpoint requires rate limiting
        RateLimited rateLimitedAnnotation = null;
        if (handler instanceof HandlerMethod handlerMethod) {
            rateLimitedAnnotation = handlerMethod.getMethodAnnotation(RateLimited.class);
            if (rateLimitedAnnotation == null) {
                rateLimitedAnnotation = handlerMethod.getBeanType().getAnnotation(RateLimited.class);
            }
        }

        String requestPath = request.getRequestURI();
        boolean matchesProtectedPath = properties.getProtectedPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));

        if (rateLimitedAnnotation == null && !matchesProtectedPath) {
            // Not a rate-limited endpoint (e.g. /api/v1/public)
            return true;
        }

        // Resolve rate limit settings
        long capacity = (rateLimitedAnnotation != null && rateLimitedAnnotation.capacity() > 0)
                ? rateLimitedAnnotation.capacity()
                : properties.getDefaultCapacity();

        double refillRate = (rateLimitedAnnotation != null && rateLimitedAnnotation.refillRate() > 0)
                ? rateLimitedAnnotation.refillRate()
                : properties.getDefaultRefillRate();

        // Resolve client identifier (API Key or Client IP)
        String clientIdentifier = resolveClientIdentifier(request, rateLimitedAnnotation);

        // Execute atomic Redis Token Bucket check
        RateLimitResult result = rateLimiterService.tryAcquire(clientIdentifier, capacity, refillRate);

        // Populate standardized rate limit headers
        response.setHeader(HEADER_LIMIT, String.valueOf(result.capacity()));
        response.setHeader(HEADER_REMAINING, String.valueOf(result.remainingTokens()));
        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));

        if (result.allowed()) {
            return true;
        }

        // Request blocked: Return 429 Too Many Requests
        sendRateLimitExceededResponse(response, result, clientIdentifier);
        return false;
    }

    private String resolveClientIdentifier(HttpServletRequest request, RateLimited annotation) {
        String apiKeyHeader = properties.getApiKeyHeader();
        String apiKey = request.getHeader(apiKeyHeader);

        String identifier;
        if (StringUtils.hasText(apiKey)) {
            identifier = "apikey:" + apiKey.trim();
        } else {
            identifier = "ip:" + extractClientIp(request);
        }

        if (annotation != null && StringUtils.hasText(annotation.keyPrefix())) {
            return annotation.keyPrefix() + ":" + identifier;
        }
        return identifier;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            // If routed via multiple proxies, first IP is the original client
            return xForwardedFor.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null) ? remoteAddr : "unknown";
    }

    private void sendRateLimitExceededResponse(
            HttpServletResponse response,
            RateLimitResult result,
            String clientIdentifier
    ) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        RateLimitErrorResponse errorResponse = RateLimitErrorResponse.of(
                result.capacity(),
                result.remainingTokens(),
                result.retryAfterSeconds(),
                clientIdentifier
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        response.getWriter().flush();
    }
}
