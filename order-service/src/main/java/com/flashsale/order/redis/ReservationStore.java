package com.flashsale.order.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Redis side of reservations. Every state change runs as a Lua script, so it is atomic. */
@Component
public class ReservationStore {

    /** How long claimed and expired reservations stay readable, so late requests get a clear answer. */
    private static final Duration MARKER_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final RedisScript<List> reserveScript = script("scripts/reserve.lua", List.class);
    private final RedisScript<List> claimScript = script("scripts/claim.lua", List.class);
    private final RedisScript<Long> releaseScript = script("scripts/release.lua", Long.class);
    private final RedisScript<Long> returnStockScript = script("scripts/return-stock.lua", Long.class);
    private final int sweepBatchSize;

    public ReservationStore(StringRedisTemplate redis,
                            @Value("${flashsale.reservations.sweep-batch-size:500}") int sweepBatchSize) {
        this.redis = redis;
        this.sweepBatchSize = sweepBatchSize;
    }

    private static <T> RedisScript<T> script(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }

    public ReserveResult reserve(String userId, long productId, int quantity, Duration hold) {
        UUID reservationId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(hold);
        List<?> result = redis.execute(reserveScript,
                List.of(RedisKeys.stock(productId), RedisKeys.userReservation(productId, userId),
                        RedisKeys.reservation(reservationId), RedisKeys.EXPIRING),
                reservationId.toString(), userId, String.valueOf(productId), String.valueOf(quantity),
                String.valueOf(expiresAt.toEpochMilli()), String.valueOf(hold.toMillis()));

        long code = (Long) result.get(0);
        if (code == 1 || code == 2) {
            UUID id = UUID.fromString((String) result.get(1));
            Instant expiry = Instant.ofEpochMilli(Long.parseLong((String) result.get(2)));
            return new ReserveResult(code == 1 ? ReserveResult.Outcome.CREATED : ReserveResult.Outcome.EXISTING,
                    id, expiry);
        }
        if (code == -1) {
            return new ReserveResult(ReserveResult.Outcome.SOLD_OUT, null, null);
        }
        return new ReserveResult(ReserveResult.Outcome.NO_COUNTER, null, null);
    }

    public ClaimResult claim(UUID reservationId, String userId) {
        List<?> result = redis.execute(claimScript,
                List.of(RedisKeys.reservation(reservationId), RedisKeys.EXPIRING),
                reservationId.toString(), userId, String.valueOf(Instant.now().toEpochMilli()),
                String.valueOf(MARKER_TTL.toSeconds()));

        long code = (Long) result.get(0);
        return switch ((int) code) {
            case 1 -> ClaimResult.claimed(Long.parseLong((String) result.get(1)),
                    Integer.parseInt((String) result.get(2)));
            case -2 -> ClaimResult.of(ClaimResult.Outcome.EXPIRED);
            case -3 -> ClaimResult.of(ClaimResult.Outcome.ALREADY_USED);
            default -> ClaimResult.of(ClaimResult.Outcome.NOT_FOUND);
        };
    }

    /** Reservation ids whose hold has passed, oldest first. */
    public Set<String> expiredReservationIds(Instant now) {
        Set<String> ids = redis.opsForZSet().rangeByScore(RedisKeys.EXPIRING, 0, now.toEpochMilli(), 0, sweepBatchSize);
        return ids == null ? Set.of() : ids;
    }

    /** Returns true if this call released the reservation, false if an order claimed it first. */
    public boolean release(String reservationId) {
        Long released = redis.execute(releaseScript,
                List.of(RedisKeys.EXPIRING, RedisKeys.reservation(UUID.fromString(reservationId))),
                reservationId, String.valueOf(MARKER_TTL.toSeconds()));
        return released != null && released == 1;
    }

    /** Puts units back on the counter after an order that claimed them did not go through. */
    public void returnStock(long productId, int quantity) {
        redis.execute(returnStockScript, List.of(RedisKeys.stock(productId)), String.valueOf(quantity));
    }

    /** Sets the counter only if it does not exist. Returns true if it was set. */
    public boolean initStockIfMissing(long productId, int available) {
        Boolean set = redis.opsForValue().setIfAbsent(RedisKeys.stock(productId), String.valueOf(available));
        return Boolean.TRUE.equals(set);
    }

    public Integer stock(long productId) {
        String value = redis.opsForValue().get(RedisKeys.stock(productId));
        return value == null ? null : Integer.valueOf(value);
    }
}
