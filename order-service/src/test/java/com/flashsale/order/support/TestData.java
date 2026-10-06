package com.flashsale.order.support;

import com.flashsale.order.redis.RedisKeys;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Resets Postgres and Redis to the seeded state between tests and reads back the results. */
public class TestData {

    public static final long PRODUCT_ID = 1L;
    public static final int SEEDED_STOCK = 100;

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public TestData(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    public void reset() {
        jdbc.execute("TRUNCATE payments, orders");
        jdbc.update("UPDATE products SET total_stock = ?, available_stock = ?, version = 0",
                SEEDED_STOCK, SEEDED_STOCK);
        flushRedis();
        redis.opsForValue().set(RedisKeys.stock(PRODUCT_ID), String.valueOf(SEEDED_STOCK));
    }

    public void flushRedis() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    /** Sets Postgres and Redis stock together. */
    public void setStock(long productId, int stock) {
        jdbc.update("UPDATE products SET total_stock = ?, available_stock = ? WHERE id = ?", stock, stock, productId);
        redis.opsForValue().set(RedisKeys.stock(productId), String.valueOf(stock));
    }

    public int availableStock(long productId) {
        return jdbc.queryForObject("SELECT available_stock FROM products WHERE id = ?", Integer.class, productId);
    }

    public Integer redisStock(long productId) {
        String value = redis.opsForValue().get(RedisKeys.stock(productId));
        return value == null ? null : Integer.valueOf(value);
    }

    public long openReservations() {
        Long size = redis.opsForZSet().zCard(RedisKeys.EXPIRING);
        return size == null ? 0 : size;
    }

    public int orderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class);
    }

    public String orderStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?::uuid", String.class, orderId);
    }
}
