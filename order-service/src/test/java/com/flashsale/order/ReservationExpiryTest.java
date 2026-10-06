package com.flashsale.order;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.flashsale.order.support.TestData.PRODUCT_ID;
import static com.flashsale.order.support.TestData.SEEDED_STOCK;
import static org.assertj.core.api.Assertions.assertThat;

/** Reservations held for one second, so expiry and the expiry-versus-order race happen quickly. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "flashsale.reservations.hold=1s",
        "flashsale.reservations.sweep-interval-ms=100"
})
class ReservationExpiryTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    TestData data;

    Http http;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        data.reset();
    }

    private HttpResponse<String> reserve(String userId) throws Exception {
        return http.post("/api/v1/reservations", Map.of("X-User-Id", userId), "{\"productId\":" + PRODUCT_ID + "}");
    }

    private HttpResponse<String> order(String userId, String reservationId) throws Exception {
        return http.post("/api/v1/orders", Map.of("X-User-Id", userId, "Idempotency-Key", UUID.randomUUID().toString()),
                "{\"reservationId\":\"" + reservationId + "\"}");
    }

    private void awaitNoOpenReservations() throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(5);
        while (data.openReservations() > 0 && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertThat(data.openReservations()).isZero();
    }

    @Test
    void abandonedReservationReturnsItsUnitAndOrderGets410() throws Exception {
        String reservationId = Json.field(reserve("user-1").body(), "reservationId");
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(SEEDED_STOCK - 1);

        awaitNoOpenReservations();

        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(SEEDED_STOCK);
        assertThat(order("user-1", reservationId).statusCode()).isEqualTo(410);
        assertThat(data.orderCount()).isZero();
        assertThat(data.redisStock(PRODUCT_ID)).isEqualTo(SEEDED_STOCK);
    }

    @Test
    void userCanReserveAgainAfterExpiry() throws Exception {
        String first = Json.field(reserve("user-1").body(), "reservationId");
        awaitNoOpenReservations();

        HttpResponse<String> again = reserve("user-1");

        assertThat(again.statusCode()).isEqualTo(201);
        assertThat(Json.field(again.body(), "reservationId")).isNotEqualTo(first);
    }

    /**
     * Orders land right around the expiry moment. Each unit must end up either sold or released,
     * never both and never neither.
     */
    @Test
    void orderAtTheExpiryBoundaryNeverDoubleCounts() throws Exception {
        int attempts = 25;
        int ordered = 0;
        int expired = 0;
        for (int i = 0; i < attempts; i++) {
            HttpResponse<String> reserved = reserve("race-user-" + i);
            Instant expiresAt = Instant.parse(Json.field(reserved.body(), "expiresAt"));
            long jitter = ThreadLocalRandom.current().nextLong(-40, 40);
            Duration wait = Duration.between(Instant.now(), expiresAt.plusMillis(jitter));
            if (!wait.isNegative()) {
                Thread.sleep(wait.toMillis());
            }
            int status = order("race-user-" + i, Json.field(reserved.body(), "reservationId")).statusCode();
            assertThat(status).isIn(201, 410);
            if (status == 201) {
                ordered++;
            } else {
                expired++;
            }
        }
        awaitNoOpenReservations();

        System.out.printf("Boundary race: %d ordered, %d expired%n", ordered, expired);
        assertThat(data.orderCount()).isEqualTo(ordered);
        assertThat(data.redisStock(PRODUCT_ID) + data.orderCount()).isEqualTo(SEEDED_STOCK);
        assertThat(data.availableStock(PRODUCT_ID) + data.orderCount()).isEqualTo(SEEDED_STOCK);
    }
}
