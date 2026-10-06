package com.flashsale.order.service;

import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.domain.Product;
import com.flashsale.order.exception.IdempotencyKeyReusedException;
import com.flashsale.order.exception.NotFoundException;
import com.flashsale.order.exception.SaleNotOpenException;
import com.flashsale.order.exception.SoldOutException;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.service.stock.StockService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final ProductService productService;
    private final OrderRepository orders;
    private final StockService stockService;
    private final TransactionTemplate tx;

    public OrderService(ProductService productService, OrderRepository orders, StockService stockService,
                        TransactionTemplate tx) {
        this.productService = productService;
        this.orders = orders;
        this.stockService = stockService;
        this.tx = tx;
    }

    /**
     * Places an order, or returns the existing one if this user already used this idempotency key.
     * <p>
     * The stock decrement and the order insert share one transaction. When two requests with the
     * same key race, the second insert hits UNIQUE (user_id, idempotency_key); its transaction
     * rolls back, including its decrement, and it returns the order the first request created.
     */
    public Order placeOrder(String userId, String idempotencyKey, long productId) {
        Optional<Order> existing = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), productId);
        }
        try {
            return tx.execute(status -> createOrder(userId, idempotencyKey, productId));
        } catch (DataIntegrityViolationException e) {
            Order first = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElseThrow(() -> e);
            return replay(first, productId);
        }
    }

    private Order createOrder(String userId, String idempotencyKey, long productId) {
        Product product = productService.getProduct(productId);
        if (!product.isSaleOpen(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new SaleNotOpenException(productId);
        }
        if (!stockService.tryDecrement(productId, 1)) {
            throw new SoldOutException(productId);
        }
        Order order = new Order(userId, productId, 1, product.getPrice(), OrderStatus.CONFIRMED,
                idempotencyKey, null);
        return orders.saveAndFlush(order);
    }

    /** A reused key is only valid for the same request; anything else is a client error. */
    private static Order replay(Order existing, long productId) {
        if (existing.getProductId() != productId) {
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
