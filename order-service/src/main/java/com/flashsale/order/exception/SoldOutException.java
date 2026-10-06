package com.flashsale.order.exception;

public class SoldOutException extends RuntimeException {
    public SoldOutException(long productId) {
        super("Product " + productId + " is sold out");
    }
}
