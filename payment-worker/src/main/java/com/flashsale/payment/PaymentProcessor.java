package com.flashsale.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Charges each order at most once. Kafka can deliver order.created more than once, so the payment
 * row is the record of the charge: UNIQUE (order_id) lets only one row exist per order, and a
 * redelivery republishes the stored result instead of charging again.
 */
@Service
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    public static final String PAYMENT_RESULT = "payment.result";

    private final JdbcTemplate jdbc;
    private final PaymentSimulator simulator;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final boolean haltAfterRecord;

    public PaymentProcessor(JdbcTemplate jdbc, PaymentSimulator simulator, KafkaTemplate<String, String> kafka,
                            JsonMapper json, @Value("${payment.halt-after-record:false}") boolean haltAfterRecord) {
        this.jdbc = jdbc;
        this.simulator = simulator;
        this.kafka = kafka;
        this.json = json;
        this.haltAfterRecord = haltAfterRecord;
    }

    public String process(OrderCreatedEvent order) throws Exception {
        UUID orderId = order.orderId();
        Optional<String> stored = storedStatus(orderId);
        String status;
        if (stored.isPresent()) {
            status = stored.get();
            log.info("Order {} was already charged ({}); publishing the stored result again", orderId, status);
        } else {
            status = simulator.charge(order);
            int inserted = jdbc.update("""
                    INSERT INTO payments (id, order_id, status) VALUES (?, ?, ?)
                    ON CONFLICT (order_id) DO NOTHING
                    """, UUID.randomUUID(), orderId, status);
            if (inserted == 0) {
                // Another delivery of the same message recorded the payment first; use its result.
                status = storedStatus(orderId).orElseThrow();
            } else if (haltAfterRecord) {
                log.warn("payment.halt-after-record is set: stopping after recording the payment for {}", orderId);
                Runtime.getRuntime().halt(1);
            }
        }

        // Wait for the broker to acknowledge, so a failed publish is retried instead of lost.
        String payload = json.writeValueAsString(new PaymentResultEvent(orderId, status));
        kafka.send(PAYMENT_RESULT, orderId.toString(), payload).get(10, TimeUnit.SECONDS);
        return status;
    }

    private Optional<String> storedStatus(UUID orderId) {
        List<String> rows = jdbc.queryForList("SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
        return rows.stream().findFirst();
    }
}
