package com.flashsale.order.service;

import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.redis.ReservationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/** Applies a payment result to its order. */
@Service
public class OrderSettlementService {

    private static final Logger log = LoggerFactory.getLogger(OrderSettlementService.class);

    private record PendingOrder(long productId, int quantity, UUID reservationId) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ReservationStore reservations;

    public OrderSettlementService(JdbcTemplate jdbc, TransactionTemplate tx, ReservationStore reservations) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.reservations = reservations;
    }

    /**
     * Moves the order to CONFIRMED or FAILED, but only if it is still PENDING_PAYMENT, so a
     * redelivered result does nothing. A declined payment returns the unit to Postgres in the same
     * transaction, and to the Redis counter after the commit.
     */
    public void settle(UUID orderId, String paymentStatus) {
        OrderStatus target = switch (paymentStatus) {
            case "SUCCEEDED" -> OrderStatus.CONFIRMED;
            case "DECLINED" -> OrderStatus.FAILED;
            default -> throw new IllegalArgumentException("Unknown payment status " + paymentStatus);
        };

        PendingOrder settled = tx.execute(status -> {
            List<PendingOrder> rows = jdbc.query(
                    "SELECT product_id, quantity, reservation_id FROM orders WHERE id = ?",
                    (rs, i) -> new PendingOrder(rs.getLong(1), rs.getInt(2), rs.getObject(3, UUID.class)),
                    orderId);
            if (rows.isEmpty()) {
                log.warn("payment.result for unknown order {}", orderId);
                return null;
            }
            int updated = jdbc.update("""
                    UPDATE orders SET status = ?, updated_at = now()
                    WHERE id = ? AND status = 'PENDING_PAYMENT'
                    """, target.name(), orderId);
            if (updated == 0) {
                return null;
            }
            PendingOrder order = rows.getFirst();
            if (target == OrderStatus.FAILED) {
                // version is bumped so a concurrent optimistic decrement cannot overwrite this return.
                jdbc.update("""
                        UPDATE products SET available_stock = available_stock + ?, version = version + 1
                        WHERE id = ?
                        """, order.quantity(), order.productId());
            }
            return order;
        });

        // Orders placed without a reservation never took a unit from the Redis counter.
        if (settled != null && target == OrderStatus.FAILED && settled.reservationId() != null) {
            reservations.returnStock(settled.productId(), settled.quantity());
        }
    }
}
