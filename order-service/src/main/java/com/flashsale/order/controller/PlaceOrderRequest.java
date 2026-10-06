package com.flashsale.order.controller;

import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(@NotNull Long productId) {
}
