package com.flashsale.order.service.stock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Checks and decrements in one statement. Postgres takes the row lock for the UPDATE and
 * re-checks the WHERE clause against the latest committed row, so zero rows means sold out.
 */
@Component
public class AtomicStockStrategy implements StockStrategy {

    private final JdbcTemplate jdbc;

    public AtomicStockStrategy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StockStrategyType type() {
        return StockStrategyType.ATOMIC;
    }

    @Override
    public boolean tryDecrement(long productId, int quantity) {
        int updated = jdbc.update("""
                UPDATE products
                SET available_stock = available_stock - ?
                WHERE id = ? AND available_stock >= ?
                """, quantity, productId, quantity);
        return updated == 1;
    }
}
