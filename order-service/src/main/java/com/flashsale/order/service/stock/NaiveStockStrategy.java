package com.flashsale.order.service.stock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the stock, checks it in Java, then writes the new value back.
 * Two requests can read the same value and both succeed, so this oversells under concurrency.
 */
@Component
public class NaiveStockStrategy implements StockStrategy {

    private final JdbcTemplate jdbc;

    public NaiveStockStrategy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryDecrement(long productId, int quantity) {
        Integer available = jdbc.queryForObject(
                "SELECT available_stock FROM products WHERE id = ?", Integer.class, productId);
        if (available == null || available < quantity) {
            return false;
        }
        jdbc.update("UPDATE products SET available_stock = ? WHERE id = ?", available - quantity, productId);
        return true;
    }
}
