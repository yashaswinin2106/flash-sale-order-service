package com.flashsale.order.service.stock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Locks the product row with SELECT ... FOR UPDATE. Other buyers block on the lock
 * until this transaction commits, so each one reads the stock left by the previous buyer.
 */
@Component
public class PessimisticStockStrategy implements StockStrategy {

    private final JdbcTemplate jdbc;

    public PessimisticStockStrategy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StockStrategyType type() {
        return StockStrategyType.PESSIMISTIC;
    }

    @Override
    public boolean tryDecrement(long productId, int quantity) {
        Integer available = jdbc.queryForObject(
                "SELECT available_stock FROM products WHERE id = ? FOR UPDATE", Integer.class, productId);
        if (available == null || available < quantity) {
            return false;
        }
        jdbc.update("UPDATE products SET available_stock = available_stock - ? WHERE id = ?", quantity, productId);
        return true;
    }
}
