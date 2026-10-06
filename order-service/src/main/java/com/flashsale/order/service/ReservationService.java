package com.flashsale.order.service;

import com.flashsale.order.domain.Product;
import com.flashsale.order.exception.InvalidRequestException;
import com.flashsale.order.exception.SaleNotOpenException;
import com.flashsale.order.exception.SoldOutException;
import com.flashsale.order.redis.ReservationStore;
import com.flashsale.order.redis.ReserveResult;
import com.flashsale.order.redis.StockCounterLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Holds a unit for a user in Redis. Most buyers in a sale stop here with "sold out". */
@Service
public class ReservationService {

    private final ProductCatalog catalog;
    private final ReservationStore store;
    private final StockCounterLoader counterLoader;
    private final boolean enabled;
    private final Duration hold;

    public ReservationService(ProductCatalog catalog, ReservationStore store, StockCounterLoader counterLoader,
                              @Value("${flashsale.reservations.enabled:true}") boolean enabled,
                              @Value("${flashsale.reservations.hold:5m}") Duration hold) {
        this.catalog = catalog;
        this.store = store;
        this.counterLoader = counterLoader;
        this.enabled = enabled;
        this.hold = hold;
    }

    public ReserveResult reserve(String userId, long productId) {
        if (!enabled) {
            throw new InvalidRequestException("Reservations are disabled; order with a productId instead");
        }
        Product product = catalog.get(productId);
        if (!product.isSaleOpen(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new SaleNotOpenException(productId);
        }

        ReserveResult result = store.reserve(userId, productId, 1, hold);
        if (result.outcome() == ReserveResult.Outcome.NO_COUNTER) {
            counterLoader.load(productId);
            result = store.reserve(userId, productId, 1, hold);
        }
        return switch (result.outcome()) {
            case CREATED, EXISTING -> result;
            case SOLD_OUT -> throw new SoldOutException(productId);
            case NO_COUNTER -> throw new IllegalStateException("No stock counter for product " + productId);
        };
    }
}
