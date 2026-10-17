-- Fixed-window counter; the first hit starts the window's TTL.
-- KEYS[1] = rl:{rule}:{subject}   ARGV[1] = window ms
-- Returns { count in this window, ms until the window resets }.

local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end

local ttl = redis.call('PTTL', KEYS[1])
if ttl < 0 then
  -- a counter must never live forever
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
  ttl = tonumber(ARGV[1])
end

return { count, ttl }
