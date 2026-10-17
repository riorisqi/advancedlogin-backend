-- Fixed-window rate limit counter: one atomic step per request.
--
-- KEYS[1] = rl:{rule}:{subject}
-- ARGV[1] = window length in milliseconds
--
-- Returns { count in this window, milliseconds until the window resets }.
--
-- The first hit creates the key and starts its TTL; later hits only INCR. When the TTL runs
-- out, the key vanishes and the next hit starts a fresh window. (Trade-off of a fixed window:
-- a client can burst up to 2x the limit around a window boundary. A sliding window fixes that
-- at the cost of more state; for login abuse, fixed is plenty.)

local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end

local ttl = redis.call('PTTL', KEYS[1])
if ttl < 0 then
  -- Safety net: a counter must never live forever (e.g. if a previous PEXPIRE was lost).
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
  ttl = tonumber(ARGV[1])
end

return { count, ttl }
