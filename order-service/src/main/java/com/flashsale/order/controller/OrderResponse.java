package com.flashsale.order.controller;

import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        OrderStatus status,
        long productId,
        int quantity,
        BigDecimal amount,
        UUID reservationId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getStatus(), o.getProductId(), o.getQuantity(), o.getAmount(),
                o.getReservationId(), o.getCreatedAt(), o.getUpdatedAt());
    }
}
