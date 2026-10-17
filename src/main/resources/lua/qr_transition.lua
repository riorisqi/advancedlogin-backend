-- Compare-and-set for a QR session state change.
-- Redis runs the whole script without interleaving other commands, so "check the status, then
-- write" can't be split by a concurrent request. Without this, two phones scanning at the same
-- moment could both read PENDING and both "win".
--
-- KEYS[1] = qr:s:{qrId}
-- ARGV[1] = expected current status (e.g. PENDING)
-- ARGV[2] = new TTL in milliseconds (every transition restarts the clock)
-- ARGV[3..n] = field1, value1, field2, value2, ...   (fields to overwrite)
--
-- Returns  1 = updated
--          0 = status was not the expected one (someone else got there first)
--         -1 = key doesn't exist (expired)

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
