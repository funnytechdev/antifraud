-- Running mean and variance of a customer's purchase amounts, updated by Welford's algorithm.
--
-- KEYS[1]  hash of the customer: n, mean, m2 after the last transaction tx,
--          and pn, pmean, pm2 as they were before it
-- ARGV[1]  id of the current transaction
-- ARGV[2]  the value to add (the service passes the logarithm of the amount)
-- ARGV[3]  time to live of the key, ms
--
-- Returns {n, mean, m2} as they were BEFORE the current transaction: the transaction is compared
-- with the customer's history, not with a history that already includes it.
--
-- Welford's update needs no list of past amounts and is numerically stable, unlike the naive
-- sum-of-squares formula. Numbers are stored with 17 significant digits, the full precision of a double.
local s = redis.call('HMGET', KEYS[1], 'n', 'mean', 'm2', 'tx', 'pn', 'pmean', 'pm2')

-- The same transaction delivered again: answer as the first time and do not count it twice.
if s[4] == ARGV[1] then
    return {s[5], s[6], s[7]}
end

local n = tonumber(s[1]) or 0
local mean = tonumber(s[2]) or 0
local m2 = tonumber(s[3]) or 0
local x = tonumber(ARGV[2])

local n1 = n + 1
local delta = x - mean
local mean1 = mean + delta / n1
local m21 = m2 + delta * (x - mean1)

local function num(value)
    return string.format('%.17g', value)
end

redis.call('HSET', KEYS[1],
    'n', string.format('%d', n1), 'mean', num(mean1), 'm2', num(m21), 'tx', ARGV[1],
    'pn', string.format('%d', n), 'pmean', num(mean), 'pm2', num(m2))
redis.call('PEXPIRE', KEYS[1], ARGV[3])

return {string.format('%d', n), num(mean), num(m2)}
