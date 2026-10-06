package com.flashsale.order.controller;

import java.util.UUID;

/**
 * With reservations enabled (the default) send reservationId.
 * With reservations disabled send productId and the order goes straight to Postgres.
 */
public record PlaceOrderRequest(UUID reservationId, Long productId) {
}
