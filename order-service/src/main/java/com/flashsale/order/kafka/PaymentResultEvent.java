package com.flashsale.order.kafka;

import java.util.UUID;

/** Consumed from payment.result. status is SUCCEEDED or DECLINED. */
public record PaymentResultEvent(UUID orderId, String status) {
}
