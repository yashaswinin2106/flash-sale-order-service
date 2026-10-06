package com.flashsale.order.exception;

public class SaleNotOpenException extends RuntimeException {
    public SaleNotOpenException(long productId) {
        super("The sale for product " + productId + " is not open");
    }
}
