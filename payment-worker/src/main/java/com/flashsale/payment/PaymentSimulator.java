package com.flashsale.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/** Stands in for a payment provider: waits a random time, then approves or declines. */
@Component
public class PaymentSimulator {

    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String DECLINED = "DECLINED";

    private final double declineRate;
    private final long minDelayMs;
    private final long maxDelayMs;

    public PaymentSimulator(@Value("${payment.decline-rate:0.1}") double declineRate,
                            @Value("${payment.min-delay-ms:50}") long minDelayMs,
                            @Value("${payment.max-delay-ms:300}") long maxDelayMs) {
        if (declineRate < 0 || declineRate > 1) {
            throw new IllegalArgumentException("payment.decline-rate must be between 0 and 1");
        }
        this.declineRate = declineRate;
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = Math.max(minDelayMs, maxDelayMs);
    }

    public String charge(OrderCreatedEvent order) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long delay = minDelayMs == maxDelayMs ? minDelayMs : random.nextLong(minDelayMs, maxDelayMs + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while charging order " + order.orderId(), e);
        }
        return random.nextDouble() < declineRate ? DECLINED : SUCCEEDED;
    }
}
