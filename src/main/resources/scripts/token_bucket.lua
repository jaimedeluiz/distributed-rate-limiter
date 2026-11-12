-- Distributed Token Bucket Rate Limiter
-- KEYS[1]: Rate limit key (e.g. rate_limit:ip:127.0.0.1 or rate_limit:key:api_key)
-- ARGV[1]: Bucket capacity (maximum tokens)
-- ARGV[2]: Refill rate in tokens per second
-- ARGV[3]: Requested tokens count (typically 1)
-- ARGV[4]: Current epoch time in milliseconds

local rate_limit_key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now_millis = tonumber(ARGV[4])

-- Retrieve current state from Redis Hash
local data = redis.call('HMGET', rate_limit_key, 'tokens', 'last_refreshed')
local current_tokens = tonumber(data[1])
local last_refreshed = tonumber(data[2])

if current_tokens == nil or last_refreshed == nil then
    -- Bucket initialization
    current_tokens = capacity
    last_refreshed = now_millis
else
    -- Calculate newly generated tokens since last request
    local elapsed_seconds = math.max(0, (now_millis - last_refreshed) / 1000.0)
    local generated_tokens = elapsed_seconds * refill_rate
    current_tokens = math.min(capacity, current_tokens + generated_tokens)
    last_refreshed = now_millis
end

local allowed = 0
local retry_after_ms = 0

if current_tokens >= requested then
    allowed = 1
    current_tokens = current_tokens - requested
else
    allowed = 0
    local missing_tokens = requested - current_tokens
    retry_after_ms = math.ceil((missing_tokens / refill_rate) * 1000)
end

-- Save updated state back to Redis
redis.call('HMSET', rate_limit_key, 'tokens', current_tokens, 'last_refreshed', last_refreshed)

-- Set TTL to auto-expire idle buckets and prevent memory leaks
-- TTL = time to replenish empty bucket to max capacity * 2 + buffer (minimum 60s)
local fill_time_seconds = math.ceil(capacity / refill_rate)
local ttl = math.max(60, fill_time_seconds * 2)
redis.call('EXPIRE', rate_limit_key, ttl)

-- Return: { allowed (1 or 0), remaining_tokens (integer), retry_after_ms, capacity }
return { allowed, math.floor(current_tokens), retry_after_ms, capacity }
