package com.flashsale.order;

import com.flashsale.order.domain.Order;
import com.flashsale.order.exception.IdempotencyKeyReusedException;
import com.flashsale.order.service.OrderService;
import com.flashsale.order.support.Concurrently;
import com.flashsale.order.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.flashsale.order.support.TestData.PRODUCT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class IdempotencyTest {

    @Autowired
    OrderService orderService;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        data.reset();
    }

    @Test
    void fiftyParallelRetriesCreateOneOrder() throws Exception {
        String key = UUID.randomUUID().toString();

        List<Object> results = Concurrently.run(50, i -> () ->
                orderService.placeOrder("user-1", key, PRODUCT_ID));

        assertThat(results).allMatch(r -> r instanceof Order);
        Set<UUID> orderIds = results.stream().map(r -> ((Order) r).getId()).collect(Collectors.toSet());
        assertThat(orderIds).hasSize(1);
        assertThat(data.orderCount()).isEqualTo(1);
        assertThat(data.availableStock(PRODUCT_ID)).isEqualTo(99);
    }

    @Test
    void sameKeyFromAnotherUserIsADifferentOrder() {
        String key = UUID.randomUUID().toString();

        Order first = orderService.placeOrder("user-1", key, PRODUCT_ID);
        Order second = orderService.placeOrder("user-2", key, PRODUCT_ID);

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(data.availableStock(PRODUCT_ID)).isEqualTo(98);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() {
        String key = UUID.randomUUID().toString();
        orderService.placeOrder("user-1", key, PRODUCT_ID);

        assertThatThrownBy(() -> orderService.placeOrder("user-1", key, 2L))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(data.availableStock(PRODUCT_ID)).isEqualTo(99);
    }
}
