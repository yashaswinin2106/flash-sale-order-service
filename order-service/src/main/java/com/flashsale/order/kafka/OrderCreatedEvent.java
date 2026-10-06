package com.flashsale.order.kafka;

import com.flashsale.order.domain.Order;

import java.math.BigDecimal;
import java.util.UUID;

/** Published to order.created, keyed by orderId. */
public record OrderCreatedEvent(UUID orderId, String userId, long productId, int quantity, BigDecimal amount) {

    public static OrderCreatedEvent from(Order order) {
        return new OrderCreatedEvent(order.getId(), order.getUserId(), order.getProductId(), order.getQuantity(),
                order.getAmount());
    }
}
