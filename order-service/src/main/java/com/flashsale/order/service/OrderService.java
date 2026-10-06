package com.flashsale.order.service;

import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.domain.Product;
import com.flashsale.order.exception.NotFoundException;
import com.flashsale.order.exception.SaleNotOpenException;
import com.flashsale.order.exception.SoldOutException;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.service.stock.StockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class OrderService {

    private final ProductService productService;
    private final OrderRepository orders;
    private final StockService stockService;

    public OrderService(ProductService productService, OrderRepository orders, StockService stockService) {
        this.productService = productService;
        this.orders = orders;
        this.stockService = stockService;
    }

    @Transactional
    public Order placeOrder(String userId, String idempotencyKey, long productId) {
        Product product = productService.getProduct(productId);
        if (!product.isSaleOpen(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new SaleNotOpenException(productId);
        }
        if (!stockService.tryDecrement(productId, 1)) {
            throw new SoldOutException(productId);
        }
        BigDecimal amount = product.getPrice();
        Order order = new Order(userId, productId, 1, amount, OrderStatus.CONFIRMED, idempotencyKey, null);
        return orders.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId, String userId) {
        return orders.findById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
    }
}
