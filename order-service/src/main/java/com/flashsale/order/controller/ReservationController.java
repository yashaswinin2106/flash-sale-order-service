package com.flashsale.order.controller;

import com.flashsale.order.redis.ReserveResult;
import com.flashsale.order.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    public record ReserveRequest(@NotNull Long productId) {
    }

    public record ReservationResponse(UUID reservationId, long productId, Instant expiresAt) {
    }

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** 201 for a new reservation, 200 if the user already holds one for this product. */
    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(@RequestHeader("X-User-Id") String userId,
                                                       @Valid @RequestBody ReserveRequest request) {
        ReserveResult result = reservationService.reserve(userId, request.productId());
        HttpStatus status = result.outcome() == ReserveResult.Outcome.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(new ReservationResponse(result.reservationId(), request.productId(), result.expiresAt()));
    }
}
