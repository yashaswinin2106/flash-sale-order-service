package com.flashsale.payment;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderCreatedListener {

    public static final String ORDER_CREATED = "order.created";

    private final PaymentProcessor processor;
    private final JsonMapper json;

    public OrderCreatedListener(PaymentProcessor processor, JsonMapper json) {
        this.processor = processor;
        this.json = json;
    }

    /** The offset is committed only after process() returns, so a crash means redelivery, not loss. */
    @KafkaListener(topics = ORDER_CREATED, groupId = "payment-worker")
    public void onOrderCreated(String payload) throws Exception {
        OrderCreatedEvent event = json.readValue(payload, OrderCreatedEvent.class);
        if (event.orderId() == null) {
            throw new IllegalArgumentException("order.created is missing orderId: " + payload);
        }
        processor.process(event);
    }
}
