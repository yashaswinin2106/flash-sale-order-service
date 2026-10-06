package com.flashsale.order.redis;

import com.flashsale.order.domain.Product;
import com.flashsale.order.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Builds the Redis stock counter from Postgres when it is missing, on startup or after Redis
 * lost its data. Reservations that were open are lost with it; those units are counted as
 * available again, and Postgres still rejects any sale beyond its own stock.
 */
@Component
public class StockCounterLoader {

    private static final Logger log = LoggerFactory.getLogger(StockCounterLoader.class);

    private final ProductRepository products;
    private final ReservationStore store;

    public StockCounterLoader(ProductRepository products, ReservationStore store) {
        this.products = products;
        this.store = store;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadAll() {
        products.findAll().forEach(this::load);
    }

    public void load(long productId) {
        products.findById(productId).ifPresent(this::load);
    }

    private void load(Product product) {
        if (store.initStockIfMissing(product.getId(), product.getAvailableStock())) {
            log.info("Loaded stock counter for product {} from Postgres: {}", product.getId(),
                    product.getAvailableStock());
        }
    }
}
