package com.flashsale.order.redis;

import java.util.UUID;

public final class RedisKeys {

    /** Sorted set of active reservation ids, scored by expiry time in epoch milliseconds. */
    public static final String EXPIRING = "reservations:expiring";

    private RedisKeys() {
    }

    /** Units not yet reserved or sold. */
    public static String stock(long productId) {
        return "stock:" + productId;
    }

    /** Hash with userId, productId, quantity, expiresAt and status. */
    public static String reservation(UUID reservationId) {
        return "reservation:" + reservationId;
    }

    /** The user's active reservation id for a product, so each user holds one at a time. */
    public static String userReservation(long productId, String userId) {
        return "user-reservation:" + productId + ":" + userId;
    }
}
