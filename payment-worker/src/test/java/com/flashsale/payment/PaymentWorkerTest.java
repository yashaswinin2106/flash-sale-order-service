package com.flashsale.payment;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "payment.decline-rate=0",
        "payment.min-delay-ms=0",
        "payment.max-delay-ms=10"
})
class PaymentWorkerTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JdbcTemplate jdbc;

    private UUID insertPendingOrder() {
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, user_id, product_id, quantity, amount, status, idempotency_key)
                VALUES (?, 'user-1', 1, 1, 99.99, 'PENDING_PAYMENT', ?)
                """, orderId, UUID.randomUUID().toString());
        return orderId;
    }

    private void sendOrderCreated(UUID orderId) {
        kafka.send(OrderCreatedListener.ORDER_CREATED, orderId.toString(),
                "{\"orderId\":\"" + orderId + "\",\"userId\":\"user-1\",\"productId\":1,\"quantity\":1,\"amount\":99.99}");
    }

    private int paymentCount(UUID orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?", Integer.class, orderId);
    }

    /** Collects records on a topic for the given key until the time runs out. */
    private static List<String> read(String topic, String key, Duration duration) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestcontainersConfiguration.kafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> values = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(duration);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    if (key.equals(record.key())) {
                        values.add(record.value());
                    }
                }
            }
        }
        return values;
    }

    @Test
    void chargesTheOrderAndPublishesTheResult() {
        UUID orderId = insertPendingOrder();

        sendOrderCreated(orderId);

        List<String> results = read(PaymentProcessor.PAYMENT_RESULT, orderId.toString(), Duration.ofSeconds(10));
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).contains("\"status\":\"SUCCEEDED\"");
        assertThat(paymentCount(orderId)).isEqualTo(1);
    }

    @Test
    void redeliveredMessageDoesNotChargeTwice() {
        UUID orderId = insertPendingOrder();

        sendOrderCreated(orderId);
        sendOrderCreated(orderId);
        sendOrderCreated(orderId);

        List<String> results = read(PaymentProcessor.PAYMENT_RESULT, orderId.toString(), Duration.ofSeconds(10));
        assertThat(paymentCount(orderId)).isEqualTo(1);
        assertThat(results).hasSize(3).allMatch(r -> r.contains("\"status\":\"SUCCEEDED\""));
    }

    @Test
    void malformedMessageGoesToTheDeadLetterTopicAndTheWorkerKeepsGoing() {
        String key = "bad-" + UUID.randomUUID();
        kafka.send(OrderCreatedListener.ORDER_CREATED, key, "{not json");

        assertThat(read(OrderCreatedListener.ORDER_CREATED + ".DLT", key, Duration.ofSeconds(10)))
                .containsExactly("{not json");

        UUID orderId = insertPendingOrder();
        sendOrderCreated(orderId);
        assertThat(read(PaymentProcessor.PAYMENT_RESULT, orderId.toString(), Duration.ofSeconds(10))).hasSize(1);
    }

    @Test
    void messageForAnUnknownOrderEndsUpInTheDeadLetterTopic() {
        UUID unknown = UUID.randomUUID();

        sendOrderCreated(unknown);

        // The payments foreign key rejects it on every retry, then it is dead-lettered.
        assertThat(read(OrderCreatedListener.ORDER_CREATED + ".DLT", unknown.toString(), Duration.ofSeconds(15)))
                .hasSize(1);
        assertThat(paymentCount(unknown)).isZero();
    }
}
