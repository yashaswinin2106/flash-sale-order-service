package com.flashsale.order.exception;

import java.util.UUID;

public class ReservationAlreadyUsedException extends RuntimeException {
    public ReservationAlreadyUsedException(UUID reservationId) {
        super("Reservation " + reservationId + " was already used by another order");
    }
}
