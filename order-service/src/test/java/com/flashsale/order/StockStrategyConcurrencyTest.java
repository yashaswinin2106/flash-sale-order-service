package com.flashsale.order;

import com.flashsale.order.exception.SoldOutException;
import com.flashsale.order.controller.PlaceOrderRequest;
import com.flashsale.order.service.OrderService;
import com.flashsale.order.service.stock.StockService;
import com.flashsale.order.support.Concurrently;
import com.flashsale.order.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static com.flashsale.order.support.TestData.PRODUCT_ID;
import static org.assertj.core.api.Assertions.assertThat;

/** 1,000 buyers race for 100 units. Every safe strategy must sell exactly 100. */
public abstract class StockStrategyConcurrencyTest {

    static final int BUYERS = 1000;
    static final int STOCK = 100;

    @Autowired
    OrderService orderService;

    @Autowired
    StockService stockService;

    @Autowired
    TestData data;

    @BeforeEach
    void setUp() {
        data.reset();
    }

    @Test
    void sellsExactlyTheAvailableStock() throws Exception {
        long start = System.nanoTime();
        List<Object> results = Concurrently.run(BUYERS, i -> () ->
                orderService.placeOrder("user-" + i, UUID.randomUUID().toString(), direct(PRODUCT_ID)));
        long millis = (System.nanoTime() - start) / 1_000_000;

        long soldOut = results.stream().filter(r -> r instanceof SoldOutException).count();
        List<Object> unexpected = results.stream()
                .filter(r -> r instanceof Exception && !(r instanceof SoldOutException))
                .toList();

        System.out.printf("%s: %d buyers, %d ms%n", stockService.activeType(), BUYERS, millis);
        assertThat(unexpected).isEmpty();
        assertThat(data.orderCount()).isEqualTo(STOCK);
        assertThat(data.availableStock(PRODUCT_ID)).isZero();
        assertThat(soldOut).isEqualTo(BUYERS - STOCK);
    }

    static PlaceOrderRequest direct(long productId) {
        return new PlaceOrderRequest(null, productId);
    }
}
