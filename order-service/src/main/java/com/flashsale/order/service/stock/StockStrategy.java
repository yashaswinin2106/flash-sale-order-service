package com.flashsale.order.service.stock;

/**
 * Takes units out of products.available_stock.
 * Must be called inside an open transaction so the decrement commits or rolls back with the order.
 */
public interface StockStrategy {

    StockStrategyType type();

    /** Returns true if the units were taken, false if there is not enough stock. */
    boolean tryDecrement(long productId, int quantity);
}
