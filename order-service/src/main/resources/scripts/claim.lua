-- Claims a reservation for an order.
-- The claim and the sweeper both remove the entry from reservations:expiring,
-- and only the one whose ZREM succeeds gets the unit.
-- KEYS[1] reservation:{reservationId}
-- KEYS[2] reservations:expiring
-- ARGV[1] reservationId, ARGV[2] userId, ARGV[3] now (epoch ms), ARGV[4] marker TTL (seconds)
--
-- Returns {1, productId, quantity}   claimed
--         {0}                        unknown reservation, or it belongs to another user
--         {-2}                       expired
--         {-3}                       already used by another order

local r = redis.call('HMGET', KEYS[1], 'userId', 'productId', 'quantity', 'expiresAt', 'status')
local userId, productId, quantity, expiresAt, status = r[1], r[2], r[3], r[4], r[5]

if not userId or userId ~= ARGV[2] then
    return {0}
end
if status == 'EXPIRED' then
    return {-2}
end
if status == 'CLAIMED' then
    return {-3}
end
-- Past its expiry but not swept yet: leave it for the sweeper.
if tonumber(expiresAt) <= tonumber(ARGV[3]) then
    return {-2}
end
if redis.call('ZREM', KEYS[2], ARGV[1]) == 0 then
    return {-2}
end

-- Keep the hash for a while so a late request gets a clear answer instead of "unknown".
redis.call('HSET', KEYS[1], 'status', 'CLAIMED')
redis.call('EXPIRE', KEYS[1], ARGV[4])

local userKey = 'user-reservation:' .. productId .. ':' .. userId
if redis.call('GET', userKey) == ARGV[1] then
    redis.call('DEL', userKey)
end

return {1, productId, quantity}
