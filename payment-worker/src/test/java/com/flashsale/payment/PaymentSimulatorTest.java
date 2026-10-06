package com.flashsale.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSimulatorTest {

    private static final OrderCreatedEvent ORDER =
            new OrderCreatedEvent(UUID.randomUUID(), "user-1", 1, 1, new BigDecimal("99.99"));

    @Test
    void declineRateOfOneDeclinesEverything() {
        PaymentSimulator simulator = new PaymentSimulator(1.0, 0, 0);

        assertThat(IntStream.range(0, 100).mapToObj(i -> simulator.charge(ORDER)))
                .containsOnly(PaymentSimulator.DECLINED);
    }

    @Test
    void declineRateOfZeroApprovesEverything() {
        PaymentSimulator simulator = new PaymentSimulator(0.0, 0, 0);

        assertThat(IntStream.range(0, 100).mapToObj(i -> simulator.charge(ORDER)))
                .containsOnly(PaymentSimulator.SUCCEEDED);
    }
}
