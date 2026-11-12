package com.portfolio.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimiterProperties {

    private boolean enabled = true;
    private long defaultCapacity = 10;
    private double defaultRefillRate = 2.0; // tokens per second
    private List<String> protectedPaths = new ArrayList<>(List.of("/api/v1/protected/**"));
    private String apiKeyHeader = "X-API-Key";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getDefaultCapacity() {
        return defaultCapacity;
    }

    public void setDefaultCapacity(long defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    public double getDefaultRefillRate() {
        return defaultRefillRate;
    }

    public void setDefaultRefillRate(double defaultRefillRate) {
        this.defaultRefillRate = defaultRefillRate;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
    }
}
