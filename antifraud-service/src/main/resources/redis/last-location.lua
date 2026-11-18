-- Remembers where a card was used last and returns the location to compare the current purchase with.
--
-- KEYS[1]  hash of the card with the last purchase (tx, lat, lon, ts, city)
--          and the one before it (ptx, plat, plon, pts, pcity)
-- ARGV[1]  id of the current transaction
-- ARGV[2]  latitude
-- ARGV[3]  longitude
-- ARGV[4]  event time, ms
-- ARGV[5]  city
-- ARGV[6]  time to live of the key, ms
--
-- Returns {lat, lon, ts, city} of the purchase to compare with, or an empty array for the first
-- purchase of the card.
local s = redis.call('HMGET', KEYS[1], 'tx', 'lat', 'lon', 'ts', 'city', 'ptx', 'plat', 'plon', 'pts', 'pcity')
local txId = ARGV[1]

-- The same transaction delivered again: compare it with what preceded it, exactly as the first time.
if s[1] == txId then
    if s[6] then
        return {s[7], s[8], s[9], s[10]}
    end
    return {}
end

-- A purchase older than the last known one: compare with the last one, but keep the newest location.
if s[1] and tonumber(s[4]) > tonumber(ARGV[4]) then
    return {s[2], s[3], s[4], s[5]}
end

redis.call('HSET', KEYS[1], 'tx', txId, 'lat', ARGV[2], 'lon', ARGV[3], 'ts', ARGV[4], 'city', ARGV[5])
if s[1] then
    redis.call('HSET', KEYS[1], 'ptx', s[1], 'plat', s[2], 'plon', s[3], 'pts', s[4], 'pcity', s[5])
end
redis.call('PEXPIRE', KEYS[1], ARGV[6])

if s[1] then
    return {s[2], s[3], s[4], s[5]}
end
return {}
