package com.flashsale.order.exception;

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

    @ExceptionHandler(SaleNotOpenException.class)
    public ProblemDetail handleSaleNotOpen(SaleNotOpenException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex);
    }

    private static ProblemDetail problem(HttpStatus status, RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    }
}
