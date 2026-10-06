package com.flashsale.order.service;

import com.flashsale.order.domain.Order;
import com.flashsale.order.kafka.OrderEventPublisher;
import com.flashsale.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Republishes order.created for orders that have been PENDING_PAYMENT for too long. This covers a
 * crash between the commit and the publish, and a Kafka outage. Publishing twice is safe because
 * the payment worker charges each order at most once.
 */
@Component
public class PendingOrderRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderRecoveryJob.class);

    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final OrderEventPublisher publisher;
    private final Duration stuckAfter;

    public PendingOrderRecoveryJob(JdbcTemplate jdbc, OrderRepository orders, OrderEventPublisher publisher,
                                   @Value("${flashsale.recovery.stuck-after:2m}") Duration stuckAfter) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.publisher = publisher;
        this.stuckAfter = stuckAfter;
    }

    @Scheduled(fixedDelayString = "${flashsale.recovery.interval-ms:30000}")
    public int republishStuckOrders() {
        // Touching updated_at means each stuck order is republished once per stuck-after period, not every run.
        List<UUID> ids = jdbc.queryForList("""
                UPDATE orders SET updated_at = now()
                WHERE id IN (
                    SELECT id FROM orders
                    WHERE status = 'PENDING_PAYMENT' AND updated_at < now() - make_interval(secs => ?)
                    ORDER BY updated_at
                    LIMIT 500
                    FOR UPDATE SKIP LOCKED)
                RETURNING id
                """, UUID.class, stuckAfter.toSeconds());
        for (UUID id : ids) {
            orders.findById(id).ifPresent(this::republish);
        }
        if (!ids.isEmpty()) {
            log.info("Republished order.created for {} stuck orders", ids.size());
        }
        return ids.size();
    }

    private void republish(Order order) {
        publisher.publishOrderCreated(order);
    }
}
