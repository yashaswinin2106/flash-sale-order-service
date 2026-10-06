package com.flashsale.order;

import com.flashsale.order.controller.PlaceOrderRequest;
import com.flashsale.order.service.OrderService;
import com.flashsale.order.support.Concurrently;
import com.flashsale.order.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static com.flashsale.order.support.TestData.PRODUCT_ID;
import static org.assertj.core.api.Assertions.assertThat;

/** Shows the lost-update problem: read-check-write without a lock sells more than the stock. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"flashsale.stock.strategy=NAIVE", "flashsale.reservations.enabled=false"})
class NaiveOversellTest {

    @Autowired
    OrderService orderService;

    @Autowired
    TestData data;

    @BeforeEach
    void setUp() {
        data.reset();
    }

    @Test
    void naiveStrategyOversells() throws Exception {
        Concurrently.run(1000, i -> () ->
                orderService.placeOrder("user-" + i, UUID.randomUUID().toString(), direct(PRODUCT_ID)));

        assertThat(data.orderCount()).isGreaterThan(100);
    }

    static PlaceOrderRequest direct(long productId) {
        return new PlaceOrderRequest(null, productId);
    }
}
