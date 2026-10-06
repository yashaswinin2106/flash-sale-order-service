-- Releases an expired reservation and returns its units to the stock counter.
-- KEYS[1] reservations:expiring
-- KEYS[2] reservation:{reservationId}
-- ARGV[1] reservationId, ARGV[2] marker TTL (seconds)
--
-- Returns 1 if this call released it, 0 if an order claimed it first.

if redis.call('ZREM', KEYS[1], ARGV[1]) == 0 then
    return 0
end

local r = redis.call('HMGET', KEYS[2], 'userId', 'productId', 'quantity')
local userId, productId, quantity = r[1], r[2], r[3]
if not productId then
    return 1
end

local stockKey = 'stock:' .. productId
-- If the counter is gone (Redis was flushed) it is rebuilt from Postgres, which already counts this unit.
if redis.call('EXISTS', stockKey) == 1 then
    redis.call('INCRBY', stockKey, quantity)
end

redis.call('HSET', KEYS[2], 'status', 'EXPIRED')
redis.call('EXPIRE', KEYS[2], ARGV[2])

local userKey = 'user-reservation:' .. productId .. ':' .. userId
if redis.call('GET', userKey) == ARGV[1] then
    redis.call('DEL', userKey)
end

return 1
