package com.flashsale.payment;

import java.math.BigDecimal;
import java.util.UUID;

/** Consumed from order.created, keyed by orderId. */
public record OrderCreatedEvent(UUID orderId, String userId, long productId, int quantity, BigDecimal amount) {
}
