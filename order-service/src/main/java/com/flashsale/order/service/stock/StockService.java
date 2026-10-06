package com.flashsale.order.service.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Applies the stock strategy chosen by flashsale.stock.strategy. */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final StockStrategy active;

    public StockService(List<StockStrategy> strategies,
                        @Value("${flashsale.stock.strategy:ATOMIC}") StockStrategyType type) {
        Map<StockStrategyType, StockStrategy> byType = new EnumMap<>(StockStrategyType.class);
        strategies.forEach(s -> byType.put(s.type(), s));
        this.active = byType.get(type);
        if (active == null) {
            throw new IllegalStateException("No stock strategy for " + type);
        }
        log.info("Using {} stock strategy", type);
    }

    public StockStrategyType activeType() {
        return active.type();
    }

    /** Takes units from Postgres stock. Call inside a transaction. */
    public boolean tryDecrement(long productId, int quantity) {
        return active.tryDecrement(productId, quantity);
    }
}
