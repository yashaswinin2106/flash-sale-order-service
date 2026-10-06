package com.flashsale.payment;

import java.util.UUID;

/** Published to payment.result, keyed by orderId. status is SUCCEEDED or DECLINED. */
public record PaymentResultEvent(UUID orderId, String status) {
}
