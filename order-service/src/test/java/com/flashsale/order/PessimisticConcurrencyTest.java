package com.flashsale.order;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "flashsale.stock.strategy=PESSIMISTIC")
class PessimisticConcurrencyTest extends StockStrategyConcurrencyTest {
}
