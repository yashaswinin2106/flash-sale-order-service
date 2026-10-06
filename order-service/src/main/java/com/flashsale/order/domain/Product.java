package com.flashsale.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    // Deliberately NOT annotated with @Version. JPA would otherwise add
    // optimistic locking to every update and hide the naive oversell in layer 1.
    // The optimistic strategy will use this column explicitly in its own query.
    @Column(nullable = false)
    private int version;

    @Column(name = "sale_starts_at", nullable = false)
    private OffsetDateTime saleStartsAt;

    @Column(name = "sale_ends_at", nullable = false)
    private OffsetDateTime saleEndsAt;

    protected Product() {
        // for JPA
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public int getTotalStock() { return totalStock; }
    public int getAvailableStock() { return availableStock; }
    public int getVersion() { return version; }
    public OffsetDateTime getSaleStartsAt() { return saleStartsAt; }
    public OffsetDateTime getSaleEndsAt() { return saleEndsAt; }
}
