package com.portfolio.ratelimiter.controller;

import com.portfolio.ratelimiter.annotation.RateLimited;
import com.portfolio.ratelimiter.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ApiController {

    /**
     * Public endpoint: No rate limiting applied.
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPublicData() {
        return ResponseEntity.ok(ApiResponse.success(
                "Access granted to public resource.",
                Map.of(
                        "resource", "Public Catalog",
                        "status", "Available to all clients",
                        "rateLimited", false
                )
        ));
    }

    /**
     * Protected endpoint: Covered by default rate limiter properties (10 tokens, 2/sec refill).
     */
    @GetMapping("/protected")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProtectedData() {
        return ResponseEntity.ok(ApiResponse.success(
                "Access granted to protected resource.",
                Map.of(
                        "resource", "Sensitive User Data & Transactions",
                        "status", "Secure & Rate-Limited",
                        "rateLimited", true
                )
        ));
    }

    /**
     * Custom protected endpoint: Overrides defaults via @RateLimited annotation.
     * Bucket capacity = 5 tokens, refill rate = 1.0 token/second.
     */
    @GetMapping("/protected/custom")
    @RateLimited(capacity = 5, refillRate = 1.0, keyPrefix = "custom_tier")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCustomProtectedData() {
        return ResponseEntity.ok(ApiResponse.success(
                "Access granted to custom tier resource.",
                Map.of(
                        "resource", "Tier 2 Specialized Analytics",
                        "tierCapacity", 5,
                        "refillRate", "1.0 token/sec"
                )
        ));
    }
}
