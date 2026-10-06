package com.flashsale.order.kafka;

import com.flashsale.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes order.created after the order has committed. A failed publish is only logged: the order
 * stays PENDING_PAYMENT in Postgres and the recovery job publishes it again later.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    public OrderEventPublisher(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    public void publishOrderCreated(Order order) {
        String key = order.getId().toString();
        String payload = json.writeValueAsString(OrderCreatedEvent.from(order));
        try {
            kafka.send(KafkaTopics.ORDER_CREATED, key, payload).whenComplete((result, error) -> {
                if (error != null) {
                    log.warn("Could not publish order.created for {}; the recovery job will retry: {}",
                            key, error.getMessage());
                }
            });
        } catch (RuntimeException e) {
            // send() throws directly when the broker is unreachable for longer than max.block.ms.
            log.warn("Could not publish order.created for {}; the recovery job will retry: {}", key, e.getMessage());
        }
    }
}
