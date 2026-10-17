-- Compare-and-set: the status check and the write can't be split by a concurrent request.
-- KEYS[1] = qr:s:{qrId}   ARGV[1] = expected status   ARGV[2] = new ttl ms   ARGV[3..n] = field, value, ...
-- Returns 1 = updated, 0 = status changed meanwhile, -1 = key gone (expired).

local current = redis.call('HGET', KEYS[1], 'status')
if not current then
  return -1
end
if current ~= ARGV[1] then
  return 0
end

redis.call('HSET', KEYS[1], unpack(ARGV, 3))
redis.call('PEXPIRE', KEYS[1], ARGV[2])
return 1
