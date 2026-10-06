package com.flashsale.order.controller;

import com.flashsale.order.domain.Product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductResponse(
        long id,
        String name,
        BigDecimal price,
        int totalStock,
        int availableStock,
        OffsetDateTime saleStartsAt,
        OffsetDateTime saleEndsAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getPrice(), p.getTotalStock(),
                p.getAvailableStock(), p.getSaleStartsAt(), p.getSaleEndsAt());
    }
}
