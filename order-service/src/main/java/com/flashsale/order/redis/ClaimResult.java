package com.flashsale.order.redis;

public record ClaimResult(Outcome outcome, long productId, int quantity) {

    public enum Outcome {
        CLAIMED,
        NOT_FOUND,
        EXPIRED,
        ALREADY_USED
    }

    static ClaimResult claimed(long productId, int quantity) {
        return new ClaimResult(Outcome.CLAIMED, productId, quantity);
    }

    static ClaimResult of(Outcome outcome) {
        return new ClaimResult(outcome, 0, 0);
    }
}
