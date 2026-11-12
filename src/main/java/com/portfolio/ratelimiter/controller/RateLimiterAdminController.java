package com.portfolio.ratelimiter.controller;

import com.portfolio.ratelimiter.dto.ApiResponse;
import com.portfolio.ratelimiter.service.RateLimiterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class RateLimiterAdminController {

    private final RateLimiterService rateLimiterService;

    public RateLimiterAdminController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Resets rate limit bucket for a client identifier.
     */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetClientBucket(
            @RequestParam(name = "clientId") String clientId
    ) {
        boolean reset = rateLimiterService.resetLimit(clientId);
        return ResponseEntity.ok(ApiResponse.success(
                reset ? "Client rate limit bucket reset successfully." : "Key was not found or already empty.",
                Map.of("clientId", clientId, "reset", reset)
        ));
    }
}
