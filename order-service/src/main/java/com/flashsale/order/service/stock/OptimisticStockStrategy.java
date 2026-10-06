package com.flashsale.order.service.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Reads stock and version without locking, then updates only if the version is unchanged.
 * If another buyer got there first the update matches zero rows and we read again.
 * Under READ COMMITTED each new SELECT sees the latest committed row, so retrying
 * inside the same transaction is safe.
 */
@Component
public class OptimisticStockStrategy implements StockStrategy {

    private final JdbcTemplate jdbc;
    private final int maxAttempts;

    public OptimisticStockStrategy(JdbcTemplate jdbc,
                                   @Value("${flashsale.stock.optimistic-max-attempts:1000}") int maxAttempts) {
        this.jdbc = jdbc;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public StockStrategyType type() {
        return StockStrategyType.OPTIMISTIC;
    }

    @Override
    public boolean tryDecrement(long productId, int quantity) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT available_stock, version FROM products WHERE id = ?", productId);
            int available = (Integer) row.get("available_stock");
            int version = (Integer) row.get("version");
            if (available < quantity) {
                return false;
            }
            int updated = jdbc.update("""
                    UPDATE products
                    SET available_stock = ?, version = version + 1
                    WHERE id = ? AND version = ?
                    """, available - quantity, productId, version);
            if (updated == 1) {
                return true;
            }
            Thread.onSpinWait();
        }
        throw new StockContentionException(productId, maxAttempts);
    }
}
