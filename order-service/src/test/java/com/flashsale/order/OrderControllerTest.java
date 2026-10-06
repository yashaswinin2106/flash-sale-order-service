package com.flashsale.order;

import com.flashsale.order.support.Http;
import com.flashsale.order.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.flashsale.order.support.TestData.PRODUCT_ID;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerTest {

    private static final Pattern ORDER_ID = Pattern.compile("\"orderId\":\"([0-9a-f-]+)\"");

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    Http http;
    TestData data;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        data = new TestData(jdbc);
        data.reset();
    }

    private HttpResponse<String> placeOrder(String userId, String key, long productId) throws Exception {
        return http.post("/api/v1/orders", Map.of("X-User-Id", userId, "Idempotency-Key", key),
                "{\"productId\":" + productId + "}");
    }

    private static String orderId(HttpResponse<String> response) {
        Matcher m = ORDER_ID.matcher(response.body());
        assertThat(m.find()).as("orderId in %s", response.body()).isTrue();
        return m.group(1);
    }

    @Test
    void placesAndFetchesAnOrder() throws Exception {
        HttpResponse<String> created = placeOrder("user-1", UUID.randomUUID().toString(), PRODUCT_ID);
        assertThat(created.statusCode()).isEqualTo(201);

        String id = orderId(created);
        HttpResponse<String> fetched = http.get("/api/v1/orders/" + id, Map.of("X-User-Id", "user-1"));
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(orderId(fetched)).isEqualTo(id);
    }

    @Test
    void retryWithSameKeyReturnsTheSameOrder() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = orderId(placeOrder("user-1", key, PRODUCT_ID));
        String second = orderId(placeOrder("user-1", key, PRODUCT_ID));

        assertThat(second).isEqualTo(first);
        assertThat(data.orderCount()).isEqualTo(1);
    }

    @Test
    void keyReusedWithDifferentBodyReturns422() throws Exception {
        String key = UUID.randomUUID().toString();
        placeOrder("user-1", key, PRODUCT_ID);

        assertThat(placeOrder("user-1", key, 2L).statusCode()).isEqualTo(422);
    }

    @Test
    void otherUsersCannotSeeTheOrder() throws Exception {
        String id = orderId(placeOrder("user-1", UUID.randomUUID().toString(), PRODUCT_ID));

        assertThat(http.get("/api/v1/orders/" + id, Map.of("X-User-Id", "user-2")).statusCode()).isEqualTo(404);
    }

    @Test
    void soldOutReturns409() throws Exception {
        data.setStock(PRODUCT_ID, 0);

        assertThat(placeOrder("user-1", UUID.randomUUID().toString(), PRODUCT_ID).statusCode()).isEqualTo(409);
    }

    @Test
    void unknownProductReturns404() throws Exception {
        assertThat(placeOrder("user-1", UUID.randomUUID().toString(), 999L).statusCode()).isEqualTo(404);
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        HttpResponse<String> response = http.post("/api/v1/orders", Map.of("X-User-Id", "user-1"),
                "{\"productId\":1}");

        assertThat(response.statusCode()).isEqualTo(400);
    }
}
