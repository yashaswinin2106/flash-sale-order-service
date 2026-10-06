package com.flashsale.order;

import com.flashsale.order.controller.PlaceOrderRequest;
import com.flashsale.order.domain.Order;
import com.flashsale.order.kafka.KafkaTopics;
import com.flashsale.order.redis.ReserveResult;
import com.flashsale.order.service.OrderService;
import com.flashsale.order.service.PendingOrderRecoveryJob;
import com.flashsale.order.service.ReservationService;
import com.flashsale.order.support.TestData;
import com.flashsale.order.support.TopicReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static com.flashsale.order.support.TestData.PRODUCT_ID;
import static com.flashsale.order.support.TestData.SEEDED_STOCK;
import static org.assertj.core.api.Assertions.assertThat;

/** order.created goes out after an order; payment.result settles it. The payment worker is played by the test. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentFlowTest {

    @Autowired
    OrderService orderService;

    @Autowired
    ReservationService reservationService;

    @Autowired
    PendingOrderRecoveryJob recoveryJob;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestData data;

    @BeforeEach
    void setUp() {
        data.reset();
    }

    private Order placeOrder(String userId) {
        ReserveResult reservation = reservationService.reserve(userId, PRODUCT_ID);
        return orderService.placeOrder(userId, UUID.randomUUID().toString(),
                new PlaceOrderRequest(reservation.reservationId(), null));
    }

    private void sendPaymentResult(Order order, String status) {
        kafka.send(KafkaTopics.PAYMENT_RESULT, order.getId().toString(),
                "{\"orderId\":\"" + order.getId() + "\",\"status\":\"" + status + "\"}");
    }

    private static void await(Supplier<Boolean> condition) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(15);
        while (!condition.get() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertThat(condition.get()).isTrue();
    }

    @Test
    void newOrderIsPendingAndPublished() {
        Order order = placeOrder("user-1");

        assertThat(data.orderStatus(order.getId().toString())).isEqualTo("PENDING_PAYMENT");
        try (TopicReader reader = new TopicReader(TestcontainersConfiguration.kafkaBootstrapServers(),
                KafkaTopics.ORDER_CREATED)) {
            assertThat(reader.await(r -> r.key().equals(order.getId().toString()), Duration.ofSeconds(15)))
                    .hasValueSatisfying(r -> assertThat(r.value()).contains("\"productId\":1"));
        }
    }

    @Test
    void successfulPaymentConfirmsTheOrder() throws Exception {
        Order order = placeOrder("user-1");

        sendPaymentResult(order, "SUCCEEDED");

        await(() -> "CONFIRMED".equals(data.orderStatus(order.getId().toString())));
        assertThat(data.availableStock(PRODUCT_ID)).isEqualTo(SEEDED_STOCK - 1);
    }

    @Test
    void declinedPaymentFailsTheOrderAndReturnsTheUnitOnce() throws Exception {
        Order order = placeOrder("user-1");
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(SEEDED_STOCK - 1);

        sendPaymentResult(order, "DECLINED");
        sendPaymentResult(order, "DECLINED");
        sendPaymentResult(order, "SUCCEEDED");

        await(() -> "FAILED".equals(data.orderStatus(order.getId().toString())));
        Thread.sleep(1000);
        assertThat(data.orderStatus(order.getId().toString())).isEqualTo("FAILED");
        assertThat(data.availableStock(PRODUCT_ID)).isEqualTo(SEEDED_STOCK);
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(SEEDED_STOCK);
    }

    @Test
    void malformedPaymentResultGoesToTheDeadLetterTopic() throws Exception {
        String bad = "not json " + UUID.randomUUID();
        kafka.send(KafkaTopics.PAYMENT_RESULT, "bad-key", bad);

        try (TopicReader dlt = new TopicReader(TestcontainersConfiguration.kafkaBootstrapServers(),
                KafkaTopics.PAYMENT_RESULT + KafkaTopics.DLT_SUFFIX)) {
            assertThat(dlt.await(r -> r.value().equals(bad), Duration.ofSeconds(20))).isPresent();
        }

        // The listener keeps working after the bad message.
        Order order = placeOrder("user-1");
        sendPaymentResult(order, "SUCCEEDED");
        await(() -> "CONFIRMED".equals(data.orderStatus(order.getId().toString())));
    }

    @Test
    void recoveryJobRepublishesStuckOrders() {
        Order order = placeOrder("user-1");
        jdbc.update("UPDATE orders SET updated_at = now() - interval '10 minutes' WHERE id = ?", order.getId());

        try (TopicReader reader = new TopicReader(TestcontainersConfiguration.kafkaBootstrapServers(),
                KafkaTopics.ORDER_CREATED)) {
            assertThat(recoveryJob.republishStuckOrders()).isEqualTo(1);
            assertThat(reader.count(r -> r.key().equals(order.getId().toString()), Duration.ofSeconds(5)))
                    .isEqualTo(2);
        }
        assertThat(recoveryJob.republishStuckOrders()).isZero();
    }
}
