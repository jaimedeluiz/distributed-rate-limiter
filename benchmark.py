#!/usr/bin/env python3
"""
Distributed Rate Limiter - High Concurrency Benchmark & Validation Suite
Fires 1,000 concurrent requests to test Token Bucket enforcement and race-condition safety.
Uses Python standard library (urllib + concurrent.futures) - zero external pip dependencies needed.
"""

import sys
import time
import json
import statistics
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE_URL = "http://localhost:8080"
TOTAL_REQUESTS = 1000
CONCURRENCY = 50
TEST_API_KEY = "portfolio-stress-test-key"

def reset_rate_limit(client_id: str):
    """Resets the rate limit bucket for a given client ID."""
    url = f"{BASE_URL}/api/v1/admin/reset?clientId=apikey:{client_id}"
    req = Request(url, method="POST")
    try:
        with urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode())
            print(f"[*] Reset bucket state: {data.get('message')}")
    except Exception as e:
        print(f"[!] Note: Reset endpoint returned: {e}")

def fire_single_request(url: str, api_key: str = None) -> dict:
    """Sends a single HTTP GET request and collects metrics."""
    headers = {"User-Agent": "RateLimiterBenchmark/1.0"}
    if api_key:
        headers["X-API-Key"] = api_key

    req = Request(url, headers=headers, method="GET")
    start = time.perf_counter()

    try:
        with urlopen(req, timeout=10) as resp:
            latency_ms = (time.perf_counter() - start) * 1000.0
            return {
                "status": resp.status,
                "latency_ms": latency_ms,
                "limit": resp.headers.get("X-RateLimit-Limit"),
                "remaining": resp.headers.get("X-RateLimit-Remaining"),
                "retry_after": resp.headers.get("X-RateLimit-Retry-After-Seconds"),
                "body": resp.read().decode()
            }
    except HTTPError as e:
        latency_ms = (time.perf_counter() - start) * 1000.0
        return {
            "status": e.code,
            "latency_ms": latency_ms,
            "limit": e.headers.get("X-RateLimit-Limit"),
            "remaining": e.headers.get("X-RateLimit-Remaining"),
            "retry_after": e.headers.get("X-RateLimit-Retry-After-Seconds"),
            "body": e.read().decode() if e.fp else ""
        }
    except URLError as e:
        latency_ms = (time.perf_counter() - start) * 1000.0
        return {
            "status": 0,
            "latency_ms": latency_ms,
            "error": str(e.reason),
            "limit": None,
            "remaining": None,
            "retry_after": None,
            "body": ""
        }

def run_stress_test(endpoint: str, api_key: str = None, total_reqs: int = 1000, concurrency: int = 50):
    url = f"{BASE_URL}{endpoint}"
    print(f"\n=======================================================")
    print(f" Target Endpoint: {endpoint}")
    print(f" Total Requests:  {total_reqs} | Concurrency: {concurrency}")
    print(f" Client Identity: {'API Key [' + api_key + ']' if api_key else 'Client IP'}")
    print(f"=======================================================")

    start_time = time.perf_counter()
    results = []

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(fire_single_request, url, api_key) for _ in range(total_reqs)]
        for f in as_completed(futures):
            results.append(f.result())

    total_duration = time.perf_counter() - start_time
    status_counts = {}
    latencies = []

    sample_200_headers = None
    sample_429_body = None

    for r in results:
        code = r["status"]
        status_counts[code] = status_counts.get(code, 0) + 1
        latencies.append(r["latency_ms"])
        if code == 200 and not sample_200_headers and r["limit"]:
            sample_200_headers = {
                "X-RateLimit-Limit": r["limit"],
                "X-RateLimit-Remaining": r["remaining"]
            }
        if code == 429 and not sample_429_body and r["body"]:
            sample_429_body = r["body"]

    latencies.sort()
    p50 = statistics.median(latencies)
    p95 = latencies[int(len(latencies) * 0.95)]
    p99 = latencies[int(len(latencies) * 0.99)]
    avg_latency = statistics.mean(latencies)
    throughput = len(results) / total_duration if total_duration > 0 else 0

    print("\n--- RESULTS SUMMARY ---")
    print(f"Elapsed Time:       {total_duration:.2f} s")
    print(f"Throughput:         {throughput:.1f} req/sec")
    print(f"Status Breakdown:   {status_counts}")
    print(f"Latency Avg:        {avg_latency:.2f} ms")
    print(f"Latency P50:        {p50:.2f} ms")
    print(f"Latency P95:        {p95:.2f} ms")
    print(f"Latency P99:        {p99:.2f} ms")

    if sample_200_headers:
        print(f"\n[Sample 200 OK Headers]")
        for k, v in sample_200_headers.items():
            print(f"  {k}: {v}")

    if sample_429_body:
        print(f"\n[Sample 429 Too Many Requests Payload]")
        try:
            formatted = json.dumps(json.loads(sample_429_body), indent=2)
            print(formatted)
        except Exception:
            print(sample_429_body)

    # Verification assertions
    if "/protected" in endpoint:
        allowed = status_counts.get(200, 0)
        blocked = status_counts.get(429, 0)
        print("\n--- VERIFICATION CHECK ---")
        if allowed <= 25 and blocked >= (total_reqs - 25):
            print(f" PASS: Rate limiter strictly defended API boundary! ({allowed} allowed, {blocked} blocked)")
        else:
            print(f"  Check parameters: {allowed} allowed, {blocked} blocked.")

if __name__ == "__main__":
    print("Distributed Rate Limiter Benchmark Suite")
    print("Testing connectivity to Spring Boot API...")
    probe = fire_single_request(f"{BASE_URL}/api/v1/public")
    if probe["status"] == 0:
        print(f"\n[!] Error: Unable to reach {BASE_URL}. Ensure docker-compose is running:")
        print("    docker compose up --build")
        sys.exit(1)

    print("[*] Service is healthy! Beginning stress tests...\n")

    # 1. Test Public Endpoint (should receive 100% 200 OK)
    print(">>> Test 1: Public endpoint (no rate limit)")
    run_stress_test("/api/v1/public", total_reqs=100, concurrency=20)

    # 2. Reset client bucket & Test Protected Endpoint (Token Bucket under 1,000 concurrent requests)
    print("\n>>> Test 2: Protected endpoint under high burst load (1,000 requests)")
    reset_rate_limit(TEST_API_KEY)
    run_stress_test("/api/v1/protected", api_key=TEST_API_KEY, total_reqs=TOTAL_REQUESTS, concurrency=CONCURRENCY)
