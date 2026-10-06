package com.flashsale.order.service.stock;

public class StockContentionException extends RuntimeException {
    public StockContentionException(long productId, int attempts) {
        super("Could not update stock for product " + productId + " after " + attempts + " attempts");
    }
}
