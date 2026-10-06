-- Puts units back on the stock counter, but only if the counter exists.
-- A missing counter is rebuilt from Postgres, which already includes these units.
-- KEYS[1] stock:{productId}
-- ARGV[1] quantity

if redis.call('EXISTS', KEYS[1]) == 1 then
    return redis.call('INCRBY', KEYS[1], ARGV[1])
end
return -1
