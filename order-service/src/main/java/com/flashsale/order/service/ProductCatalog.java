package com.flashsale.order.service;

import com.flashsale.order.domain.Product;
import com.flashsale.order.exception.NotFoundException;
import com.flashsale.order.repository.ProductRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory copy of product details that do not change during a sale (name, price, sale window),
 * so a reservation can be checked without a Postgres round trip.
 * Stock is never read from here.
 */
@Component
public class ProductCatalog {

    private final ProductRepository products;
    private final Map<Long, Product> cache = new ConcurrentHashMap<>();

    public ProductCatalog(ProductRepository products) {
        this.products = products;
    }

    public Product get(long productId) {
        Product product = cache.computeIfAbsent(productId, id -> products.findById(id).orElse(null));
        if (product == null) {
            throw new NotFoundException("Product " + productId + " not found");
        }
        return product;
    }
}
