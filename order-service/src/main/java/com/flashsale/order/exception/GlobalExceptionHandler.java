package com.flashsale.order.exception;

import com.flashsale.order.service.stock.StockContentionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain exceptions to HTTP status codes with an RFC 9457 problem body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(SoldOutException.class)
    public ProblemDetail handleSoldOut(SoldOutException ex) {
        return problem(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ReservationAlreadyUsedException.class)
    public ProblemDetail handleAlreadyUsed(ReservationAlreadyUsedException ex) {
        return problem(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ReservationExpiredException.class)
    public ProblemDetail handleExpired(ReservationExpiredException ex) {
        return problem(HttpStatus.GONE, ex);
    }

    @ExceptionHandler({SaleNotOpenException.class, InvalidRequestException.class})
    public ProblemDetail handleBadRequest(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ProblemDetail handleKeyReused(IdempotencyKeyReusedException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex);
    }

    @ExceptionHandler(StockContentionException.class)
    public ProblemDetail handleContention(StockContentionException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex);
    }

    private static ProblemDetail problem(HttpStatus status, RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    }
}
