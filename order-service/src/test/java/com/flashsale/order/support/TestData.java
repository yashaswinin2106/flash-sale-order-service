package com.flashsale.order.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Resets the database to the seeded state between tests. */
public class TestData {

    public static final long PRODUCT_ID = 1L;
    public static final int SEEDED_STOCK = 100;

    private final JdbcTemplate jdbc;

    public TestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void reset() {
        jdbc.execute("TRUNCATE payments, orders");
        jdbc.update("UPDATE products SET total_stock = ?, available_stock = ?, version = 0", SEEDED_STOCK, SEEDED_STOCK);
    }

    public void setStock(long productId, int stock) {
        jdbc.update("UPDATE products SET total_stock = ?, available_stock = ? WHERE id = ?", stock, stock, productId);
    }

    public int availableStock(long productId) {
        return jdbc.queryForObject("SELECT available_stock FROM products WHERE id = ?", Integer.class, productId);
    }

    public int orderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class);
    }
}
