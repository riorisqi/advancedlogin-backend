-- Hash + TTL in one atomic step, so a key can never be left without an expiry.
-- KEYS[1] = qr:s:{qrId}   ARGV[1] = ttl ms   ARGV[2..n] = field, value, ...
-- Returns 1 when created, 0 if the key already existed.

if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end

redis.call('HSET', KEYS[1], unpack(ARGV, 2))
redis.call('PEXPIRE', KEYS[1], ARGV[1])
return 1
