package com.flashsale.order.service;

import com.flashsale.order.controller.PlaceOrderRequest;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.domain.Product;
import com.flashsale.order.exception.IdempotencyKeyReusedException;
import com.flashsale.order.exception.InvalidRequestException;
import com.flashsale.order.exception.NotFoundException;
import com.flashsale.order.exception.ReservationAlreadyUsedException;
import com.flashsale.order.exception.ReservationExpiredException;
import com.flashsale.order.exception.SaleNotOpenException;
import com.flashsale.order.exception.SoldOutException;
import com.flashsale.order.redis.ClaimResult;
import com.flashsale.order.redis.ReservationStore;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.service.stock.StockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OrderService {

    private final ProductService productService;
    private final OrderRepository orders;
    private final StockService stockService;
    private final ReservationStore reservations;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final boolean reservationsEnabled;

    public OrderService(ProductService productService, OrderRepository orders, StockService stockService,
                        ReservationStore reservations, TransactionTemplate tx, JdbcTemplate jdbc,
                        @Value("${flashsale.reservations.enabled:true}") boolean reservationsEnabled) {
        this.productService = productService;
        this.orders = orders;
        this.stockService = stockService;
        this.reservations = reservations;
        this.tx = tx;
        this.jdbc = jdbc;
        this.reservationsEnabled = reservationsEnabled;
    }

    /**
     * Places an order, or returns the existing one if this user already used this idempotency key.
     * <p>
     * Requests with the same user and key are serialized by a transaction-scoped advisory lock, so a
     * retry always sees the first request's order instead of racing it for the reservation.
     * UNIQUE (user_id, idempotency_key) stays as the backstop: if it fires, the transaction rolls back,
     * including the stock decrement, and the existing order is returned.
     */
    public Order placeOrder(String userId, String idempotencyKey, PlaceOrderRequest request) {
        validate(request);
        AtomicReference<ClaimResult> claimed = new AtomicReference<>();
        try {
            return tx.execute(status -> {
                lockIdempotencyKey(userId, idempotencyKey);
                Optional<Order> existing = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
                if (existing.isPresent()) {
                    return replay(existing.get(), request);
                }
                return reservationsEnabled
                        ? orderFromReservation(userId, idempotencyKey, request.reservationId(), claimed)
                        : directOrder(userId, idempotencyKey, request.productId());
            });
        } catch (RuntimeException e) {
            ClaimResult claim = claimed.get();
            // The reservation was consumed but no order was saved: give the unit back to Redis.
            // Not on SoldOutException: then Postgres had no unit for it, so the counter was too high.
            if (claim != null && !(e instanceof SoldOutException)) {
                reservations.returnStock(claim.productId(), claim.quantity());
            }
            if (e instanceof DataIntegrityViolationException) {
                Optional<Order> first = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
                if (first.isPresent()) {
                    return replay(first.get(), request);
                }
            }
            throw e;
        }
    }

    private void validate(PlaceOrderRequest request) {
        if (reservationsEnabled && request.reservationId() == null) {
            throw new InvalidRequestException("reservationId is required");
        }
        if (!reservationsEnabled && request.productId() == null) {
            throw new InvalidRequestException("productId is required");
        }
    }

    private void lockIdempotencyKey(String userId, String idempotencyKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> null, userId + ":" + idempotencyKey);
    }

    private Order orderFromReservation(String userId, String idempotencyKey, UUID reservationId,
                                       AtomicReference<ClaimResult> claimed) {
        ClaimResult claim = reservations.claim(reservationId, userId);
        switch (claim.outcome()) {
            case NOT_FOUND -> throw new NotFoundException("Reservation " + reservationId + " not found");
            case EXPIRED -> throw new ReservationExpiredException(reservationId);
            case ALREADY_USED -> throw new ReservationAlreadyUsedException(reservationId);
            case CLAIMED -> claimed.set(claim);
        }
        Product product = productService.getProduct(claim.productId());
        return saveOrder(userId, idempotencyKey, product, claim.quantity(), reservationId);
    }

    private Order directOrder(String userId, String idempotencyKey, long productId) {
        Product product = productService.getProduct(productId);
        if (!product.isSaleOpen(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new SaleNotOpenException(productId);
        }
        return saveOrder(userId, idempotencyKey, product, 1, null);
    }

    private Order saveOrder(String userId, String idempotencyKey, Product product, int quantity,
                            UUID reservationId) {
        if (!stockService.tryDecrement(product.getId(), quantity)) {
            throw new SoldOutException(product.getId());
        }
        Order order = new Order(userId, product.getId(), quantity,
                product.getPrice().multiply(BigDecimal.valueOf(quantity)),
                OrderStatus.CONFIRMED, idempotencyKey, reservationId);
        return orders.saveAndFlush(order);
    }

    /** A reused key is only valid for the same request; anything else is a client error. */
    private Order replay(Order existing, PlaceOrderRequest request) {
        boolean sameRequest = reservationsEnabled
                ? Objects.equals(existing.getReservationId(), request.reservationId())
                : Objects.equals(existing.getProductId(), request.productId());
        if (!sameRequest) {
            throw new IdempotencyKeyReusedException(existing.getIdempotencyKey());
        }
        return existing;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId, String userId) {
        return orders.findById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
    }
}
