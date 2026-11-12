# Distributed Rate Limiter API 🛡️⚡

A production-grade, distributed rate limiting middleware built with **Java 17**, **Spring Boot 3**, **Redis**, and **Docker**. Implements the **Token Bucket** algorithm using atomic **Redis Lua scripts** to eliminate race conditions under high concurrent traffic.

---

## 📌 Architecture & Design

In modern microservices, client requests must be rate-limited across multiple horizontal application replicas. Relying on in-memory counters (e.g., Guava RateLimiter) fails in distributed clusters because state is not shared across nodes.

```
                           ┌──────────────────────────┐
                           │   Incoming HTTP Request   │
                           └─────────────┬────────────┘
                                         │
                                         ▼
                       ┌───────────────────────────────────┐
                       │      RateLimiterInterceptor       │
                       │ - Extracts Client IP / X-API-Key  │
                       │ - Inspects @RateLimited / Routes  │
                       └─────────────────┬─────────────────┘
                                         │
                                         ▼
                       ┌───────────────────────────────────┐
                       │     Atomic Redis Lua Script       │
                       │ - HMGET tokens & last_refreshed   │
                       │ - Replenish tokens based on delta │
                       │ - Deduct 1 token if available     │
                       │ - Set TTL (auto memory eviction)  │
                       └─────────────┬───────────────┬─────┘
                                     │               │
                        [Tokens >= 1]│               │[Tokens < 1]
                                     ▼               ▼
                        ┌──────────────────┐  ┌─────────────────────────┐
                        │  200 OK Response │  │ 429 Too Many Requests   │
                        │  X-RateLimit-*   │  │ Descriptive JSON Error  │
                        └──────────────────┘  └─────────────────────────┘
```

### Why Redis Lua Script?
A naive distributed rate limiter performs multiple steps:
1. `GET` current tokens and timestamp from Redis.
2. Calculate token refill in Java application memory.
3. `SET` decremented token count back to Redis.

Under 1,000 concurrent requests across multiple instances, this approach suffers from severe **Check-Then-Act Race Conditions**, allowing requests to pass beyond the configured capacity. 

By executing the Token Bucket algorithm inside a **Redis Lua script**, the entire read-compute-write sequence runs **atomically and single-threaded inside Redis engine memory**, guaranteeing 100% thread safety without distributed locks or lock contention.

---

## 🚀 Key Features

- **Distributed Token Bucket Algorithm**: Supports burst capacity with smooth, continuous token refill.
- **Atomic Concurrency Guarantee**: Zero race conditions under peak traffic bursts.
- **Declarative `@RateLimited` Annotation**: Configure custom capacity and refill rates per controller endpoint or class.
- **Spring `HandlerInterceptor` Pipeline**: Non-invasive middleware separating business logic from traffic policing.
- **Industry Standard Headers**:
  - `X-RateLimit-Limit`: Maximum bucket capacity.
  - `X-RateLimit-Remaining`: Tokens currently remaining in the client bucket.
  - `X-RateLimit-Retry-After-Seconds`: Seconds until sufficient tokens replenish.
- **Descriptive HTTP 429 Responses**: Returns structured JSON with actionable error information.
- **Multi-Identity Resolution**: Prioritizes `X-API-Key` headers, falling back gracefully to client IP (handling `X-Forwarded-For` reverse-proxy headers).
- **Production Resilience (Fail-Open)**: If Redis encounters transient network timeouts, requests pass through gracefully to prevent cascade outages.
- **Self-Cleaning Storage**: Calculates dynamic Redis key TTLs to prevent unbounded memory growth.

---

## 🛠️ Tech Stack

- **Language:** Java 17+
- **Framework:** Spring Boot 3.2.5 (Spring MVC, Spring Data Redis, Spring Boot Actuator)
- **Data Store:** Redis 7.2 Alpine
- **Containerization:** Docker & Docker Compose (Multi-stage build)
- **Build Tool:** Apache Maven
- **Load Testing:** Python 3 (async/thread pool concurrency)

---

## 📁 Project Structure

```
distributed-rate-limiter/
├── Dockerfile                          # Multi-stage container build
├── docker-compose.yml                  # Redis + Spring Boot services orchestration
├── pom.xml                             # Dependencies & build configuration
├── benchmark.py                        # 1,000 concurrent request load generator
├── src/
│   ├── main/
│   │   ├── java/com/portfolio/ratelimiter/
│   │   │   ├── RateLimiterApplication.java
│   │   │   ├── annotation/RateLimited.java           # Custom declarative annotation
│   │   │   ├── config/
│   │   │   │   ├── RateLimiterProperties.java        # Configuration properties
│   │   │   │   ├── RedisConfig.java                  # RedisTemplate & Lua script bean
│   │   │   │   └── WebMvcConfig.java                 # Interceptor registration
│   │   │   ├── controller/
│   │   │   │   ├── ApiController.java                # Public & Protected demo routes
│   │   │   │   └── RateLimiterAdminController.java   # Bucket reset & admin endpoints
│   │   │   ├── dto/
│   │   │   │   ├── ApiResponse.java                  # Standard API response record
│   │   │   │   ├── RateLimitErrorResponse.java       # HTTP 429 structured JSON record
│   │   │   │   └── RateLimitResult.java              # Internal verification record
│   │   │   ├── interceptor/RateLimiterInterceptor.java # Request boundary enforcement
│   │   │   └── service/RateLimiterService.java       # Lua script execution engine
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-docker.yml
│   │       └── scripts/token_bucket.lua              # Atomic Token Bucket algorithm
│   └── test/
│       └── java/com/portfolio/ratelimiter/           # Unit & service tests
```

---

## ⚡ Quickstart

### Option 1: Run with Docker Compose (Recommended)

Start the entire system with one command:
```bash
docker compose up --build
```
Both the Spring Boot application (`localhost:8080`) and Redis (`localhost:6379`) will start with healthchecks.

### Option 2: Run Locally

1. Start Redis:
```bash
docker run -d -p 6379:6379 --name local-redis redis:7-alpine
```

2. Run the Spring Boot application:
```bash
mvn spring-boot:run
```

---

## 📡 API Endpoints

| Method | Endpoint | Description | Rate Limit |
|---|---|---|---|
| `GET` | `/api/v1/public` | Public resource | None (Unlimited) |
| `GET` | `/api/v1/protected` | Protected resource | 10 tokens, refill 2.0/sec |
| `GET` | `/api/v1/protected/custom` | Tier 2 analytics resource | 5 tokens, refill 1.0/sec |
| `POST` | `/api/v1/admin/reset?clientId=...` | Resets bucket for testing | Admin only |

---

## 🧪 Stress Testing & Verification

Run the included stress-test suite which launches 1,000 asynchronous concurrent requests:

```bash
python3 benchmark.py
```

### Sample Output:
```text
Distributed Rate Limiter Benchmark Suite
Testing connectivity to Spring Boot API...
[*] Service is healthy! Beginning stress tests...

>>> Test 1: Public endpoint (no rate limit)
=======================================================
 Target Endpoint: /api/v1/public
 Total Requests:  100 | Concurrency: 20
 Client Identity: Client IP
=======================================================
--- RESULTS SUMMARY ---
Elapsed Time:       0.14 s
Throughput:         714.2 req/sec
Status Breakdown:   {200: 100}

>>> Test 2: Protected endpoint under high burst load (1,000 requests)
=======================================================
 Target Endpoint: /api/v1/protected
 Total Requests:  1000 | Concurrency: 50
 Client Identity: API Key [portfolio-stress-test-key]
=======================================================
--- RESULTS SUMMARY ---
Elapsed Time:       0.89 s
Throughput:         1123.6 req/sec
Status Breakdown:   {200: 10, 429: 990}
Latency Avg:        12.34 ms
Latency P50:        9.81 ms
Latency P95:        24.12 ms
Latency P99:        38.45 ms

[Sample 200 OK Headers]
  X-RateLimit-Limit: 10
  X-RateLimit-Remaining: 9

[Sample 429 Too Many Requests Payload]
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Bucket capacity is 10 tokens. Please retry in 1 second(s).",
  "limit": 10,
  "remaining": 0,
  "retryAfterSeconds": 1,
  "clientIdentifier": "apikey:portfolio-stress-test-key",
  "timestamp": "2026-11-21T17:15:30.123456Z"
}

--- VERIFICATION CHECK ---
 PASS: Rate limiter strictly defended API boundary! (10 allowed, 990 blocked)
```

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).
