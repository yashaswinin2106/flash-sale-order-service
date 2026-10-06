package com.flashsale.order.redis;

import java.time.Instant;
import java.util.UUID;

public record ReserveResult(Outcome outcome, UUID reservationId, Instant expiresAt) {

    public enum Outcome {
        CREATED,
        /** The user already holds a reservation for this product; it is returned unchanged. */
        EXISTING,
        SOLD_OUT,
        /** The stock counter is missing from Redis and has to be loaded from Postgres. */
        NO_COUNTER
    }
}
