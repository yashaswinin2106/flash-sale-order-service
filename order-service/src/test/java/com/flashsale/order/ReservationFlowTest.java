package com.flashsale.order;

import com.flashsale.order.exception.SoldOutException;
import com.flashsale.order.redis.ReserveResult;
import com.flashsale.order.redis.StockCounterLoader;
import com.flashsale.order.service.ReservationService;
import com.flashsale.order.support.Concurrently;
import com.flashsale.order.support.Http;
import com.flashsale.order.support.Json;
import com.flashsale.order.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.flashsale.order.support.TestData.PRODUCT_ID;
import static org.assertj.core.api.Assertions.assertThat;

/** Reserve in Redis, then turn the reservation into an order. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationFlowTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    TestData data;

    @Autowired
    StockCounterLoader counterLoader;

    @Autowired
    ReservationService reservationService;

    Http http;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        data.reset();
    }

    private HttpResponse<String> reserve(String userId) throws Exception {
        return http.post("/api/v1/reservations", Map.of("X-User-Id", userId), "{\"productId\":" + PRODUCT_ID + "}");
    }

    private HttpResponse<String> order(String userId, String key, String reservationId) throws Exception {
        return http.post("/api/v1/orders", Map.of("X-User-Id", userId, "Idempotency-Key", key),
                "{\"reservationId\":\"" + reservationId + "\"}");
    }

    private String reservationId(String userId) throws Exception {
        HttpResponse<String> response = reserve(userId);
        assertThat(response.statusCode()).isIn(200, 201);
        return Json.field(response.body(), "reservationId");
    }

    @Test
    void reserveThenOrder() throws Exception {
        HttpResponse<String> reserved = reserve("user-1");
        assertThat(reserved.statusCode()).isEqualTo(201);
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(99);
        assertThat(data.availableStock(PRODUCT_ID)).isEqualTo(100);

        HttpResponse<String> ordered = order("user-1", UUID.randomUUID().toString(),
                Json.field(reserved.body(), "reservationId"));

        assertThat(ordered.statusCode()).isEqualTo(202);
        assertThat(data.availableStock(PRODUCT_ID)).isEqualTo(99);
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(99);
        assertThat(data.openReservations()).isZero();
    }

    @Test
    void secondReserveReturnsTheSameReservation() throws Exception {
        HttpResponse<String> first = reserve("user-1");
        HttpResponse<String> second = reserve("user-1");

        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(Json.field(second.body(), "reservationId")).isEqualTo(Json.field(first.body(), "reservationId"));
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(99);
    }

    @Test
    void reserveWhenSoldOutReturns409() throws Exception {
        data.setStock(PRODUCT_ID, 1);
        assertThat(reserve("user-1").statusCode()).isEqualTo(201);

        assertThat(reserve("user-2").statusCode()).isEqualTo(409);
    }

    @Test
    void thousandBuyersReserveExactlyTheStock() throws Exception {
        List<Object> results = Concurrently.run(1000, i -> () -> reservationService.reserve("user-" + i, PRODUCT_ID));

        assertThat(results).filteredOn(r -> r instanceof ReserveResult).hasSize(100);
        assertThat(results).filteredOn(r -> r instanceof SoldOutException).hasSize(900);
        assertThat(data.redisStock(PRODUCT_ID)).isZero();
    }

    @Test
    void fiftyParallelRetriesCreateOneOrder() throws Exception {
        String reservationId = reservationId("user-1");
        String key = UUID.randomUUID().toString();

        List<Object> results = Concurrently.run(50, i -> () -> order("user-1", key, reservationId));

        assertThat(results).allMatch(r -> r instanceof HttpResponse<?> res && res.statusCode() == 202);
        assertThat(results.stream().map(r -> Json.field(((HttpResponse<String>) r).body(), "orderId")).distinct())
                .hasSize(1);
        assertThat(data.orderCount()).isEqualTo(1);
        assertThat(data.availableStock(PRODUCT_ID)).isEqualTo(99);
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(99);
    }

    @Test
    void reservationCanOnlyBeUsedOnce() throws Exception {
        String reservationId = reservationId("user-1");
        order("user-1", UUID.randomUUID().toString(), reservationId);

        assertThat(order("user-1", UUID.randomUUID().toString(), reservationId).statusCode()).isEqualTo(409);
        assertThat(data.orderCount()).isEqualTo(1);
    }

    @Test
    void sameKeyWithAnotherReservationReturns422AndKeepsTheUnit() throws Exception {
        String key = UUID.randomUUID().toString();
        order("user-1", key, reservationId("user-1"));
        data.setStock(PRODUCT_ID, 10);
        String second = reservationId("user-1");

        assertThat(order("user-1", key, second).statusCode()).isEqualTo(422);
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(9);
    }

    @Test
    void unknownReservationReturns404() throws Exception {
        assertThat(order("user-1", UUID.randomUUID().toString(), UUID.randomUUID().toString()).statusCode())
                .isEqualTo(404);
    }

    @Test
    void anotherUsersReservationReturns404() throws Exception {
        String reservationId = reservationId("user-1");

        assertThat(order("user-2", UUID.randomUUID().toString(), reservationId).statusCode()).isEqualTo(404);
        assertThat(data.orderCount()).isZero();
    }

    @Test
    void orderWithoutReservationIdReturns400() throws Exception {
        HttpResponse<String> response = http.post("/api/v1/orders",
                Map.of("X-User-Id", "user-1", "Idempotency-Key", UUID.randomUUID().toString()),
                "{\"productId\":1}");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void counterIsRebuiltFromPostgresAfterRedisIsFlushed() throws Exception {
        order("user-1", UUID.randomUUID().toString(), reservationId("user-1"));
        data.flushRedis();

        assertThat(reserve("user-2").statusCode()).isEqualTo(201);
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(98);

        data.flushRedis();
        counterLoader.loadAll();
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(99);
    }
}
