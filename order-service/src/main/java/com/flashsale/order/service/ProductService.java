package com.flashsale.order.service;

import com.flashsale.order.domain.Product;
import com.flashsale.order.exception.NotFoundException;
import com.flashsale.order.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Product getProduct(long id) {
        return products.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }
}
