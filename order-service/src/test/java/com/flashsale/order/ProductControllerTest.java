package com.flashsale.order;

import com.flashsale.order.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductControllerTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    TestData data;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void resetData() {
        data.reset();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void returnsSeededProductWithFullStock() throws Exception {
        HttpResponse<String> response = get("/api/v1/products/1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"id\":1")
                .contains("\"totalStock\":100")
                .contains("\"availableStock\":100");
    }

    @Test
    void returns404ForUnknownProduct() throws Exception {
        HttpResponse<String> response = get("/api/v1/products/999");

        assertThat(response.statusCode()).isEqualTo(404);
    }
}
