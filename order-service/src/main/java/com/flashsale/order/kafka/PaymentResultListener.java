package com.flashsale.order.kafka;

import com.flashsale.order.service.OrderSettlementService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentResultListener {

    private final OrderSettlementService settlement;
    private final JsonMapper json;

    public PaymentResultListener(OrderSettlementService settlement, JsonMapper json) {
        this.settlement = settlement;
        this.json = json;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_RESULT, groupId = "order-service")
    public void onPaymentResult(String payload) {
        PaymentResultEvent event = json.readValue(payload, PaymentResultEvent.class);
        if (event.orderId() == null || event.status() == null) {
            throw new IllegalArgumentException("payment.result is missing orderId or status: " + payload);
        }
        settlement.settle(event.orderId(), event.status());
    }
}
