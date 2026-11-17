-- Sliding window counter over event time.
--
-- KEYS[1]  sorted set of the card: member = transaction id, score = event time in ms
-- ARGV[1]  event time of the current transaction, ms
-- ARGV[2]  window length, ms
-- ARGV[3]  id of the current transaction
-- ARGV[4]  time to live of the key, ms
--
-- Returns the number of transactions of the card in the window (now - window, now],
-- the current one included.
--
-- The whole update runs as one atomic step, so two consumers that briefly own the same partition
-- during a rebalance cannot interleave. The transaction id is the member, which makes the script
-- idempotent: a redelivered transaction overwrites itself instead of being counted twice.
local now = tonumber(ARGV[1])
local windowStart = now - tonumber(ARGV[2])

redis.call('ZADD', KEYS[1], now, ARGV[3])
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', windowStart)
redis.call('PEXPIRE', KEYS[1], ARGV[4])

return redis.call('ZCOUNT', KEYS[1], '(' .. windowStart, now)
