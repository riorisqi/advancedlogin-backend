-- Create a QR session hash and give it a TTL in one atomic step.
-- Two separate commands (HSET, then PEXPIRE) could leave a key with no expiry if the app died
-- in between; Redis runs a script without interleaving any other command.
--
-- KEYS[1] = qr:s:{qrId}
-- ARGV[1] = ttl in milliseconds
-- ARGV[2..n] = field1, value1, field2, value2, ...
--
-- Returns 1 when created, 0 if the key already existed (never overwrite a live session).

if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end

redis.call('HSET', KEYS[1], unpack(ARGV, 2))
redis.call('PEXPIRE', KEYS[1], ARGV[1])
return 1
