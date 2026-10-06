package com.flashsale.order.exception;

import java.util.UUID;

public class ReservationExpiredException extends RuntimeException {
    public ReservationExpiredException(UUID reservationId) {
        super("Reservation " + reservationId + " has expired");
    }
}
