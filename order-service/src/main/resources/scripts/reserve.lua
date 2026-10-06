-- Holds units for one user, atomically.
-- KEYS[1] stock:{productId}
-- KEYS[2] user-reservation:{productId}:{userId}
-- KEYS[3] reservation:{reservationId}
-- KEYS[4] reservations:expiring
-- ARGV[1] reservationId, ARGV[2] userId, ARGV[3] productId, ARGV[4] quantity,
-- ARGV[5] expiresAt (epoch ms), ARGV[6] hold (ms)
--
-- Returns {1, reservationId, expiresAt}    new reservation
--         {2, reservationId, expiresAt}    the user already holds one; nothing changed
--         {-1}                             sold out
--         {-3}                             stock counter missing; caller loads it and retries

local existing = redis.call('GET', KEYS[2])
if existing then
    local expiresAt = redis.call('HGET', 'reservation:' .. existing, 'expiresAt')
    return {2, existing, expiresAt or '0'}
end

local stock = redis.call('GET', KEYS[1])
if not stock then
    return {-3}
end

local quantity = tonumber(ARGV[4])
if tonumber(stock) < quantity then
    return {-1}
end

redis.call('DECRBY', KEYS[1], quantity)
redis.call('HSET', KEYS[3],
    'userId', ARGV[2],
    'productId', ARGV[3],
    'quantity', ARGV[4],
    'expiresAt', ARGV[5],
    'status', 'ACTIVE')
redis.call('ZADD', KEYS[4], ARGV[5], ARGV[1])
-- Expires with the reservation so the user can reserve again once the hold has passed.
redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[6])

return {1, ARGV[1], ARGV[5]}
