package com.flashsale.order.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Releases reservations whose hold has passed and returns their units to the stock counter.
 * Redis key expiry events are not used because Redis does not guarantee their delivery.
 * Running several instances is safe: release.lua only acts if its ZREM succeeds.
 */
@Component
@ConditionalOnProperty(name = "flashsale.reservations.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationSweeper {

    private static final Logger log = LoggerFactory.getLogger(ReservationSweeper.class);

    private final ReservationStore store;

    public ReservationSweeper(ReservationStore store) {
        this.store = store;
    }

    @Scheduled(fixedDelayString = "${flashsale.reservations.sweep-interval-ms:1000}")
    public void sweep() {
        int released = 0;
        for (String id : store.expiredReservationIds(Instant.now())) {
            if (store.release(id)) {
                released++;
            }
        }
        if (released > 0) {
            log.info("Released {} expired reservations", released);
        }
    }
}
