package com.flashsale.order.service.stock;

public enum StockStrategyType {
    /** Read, check, write. Oversells under concurrency; kept for comparison. */
    NAIVE,
    /** SELECT ... FOR UPDATE, then write. Concurrent buyers queue on the row lock. */
    PESSIMISTIC,
    /** One conditional UPDATE that checks and decrements together. */
    ATOMIC,
    /** Write only if the version is unchanged since the read; retry on conflict. */
    OPTIMISTIC
}
