package com.flashsale.order.controller;

import com.flashsale.order.domain.Order;
import com.flashsale.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 202 because payment happens afterwards; poll GET /orders/{id} for CONFIRMED or FAILED. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse placeOrder(@RequestHeader("X-User-Id") String userId,
                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                    @RequestBody PlaceOrderRequest request) {
        Order order = orderService.placeOrder(userId, idempotencyKey, request);
        return OrderResponse.from(order);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id) {
        return OrderResponse.from(orderService.getOrder(id, userId));
    }
}
