package com.flashsale.order.exception;

public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException(String key) {
        super("Idempotency key " + key + " was already used for a different request");
    }
}
