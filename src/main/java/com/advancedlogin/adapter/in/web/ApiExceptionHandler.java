package com.advancedlogin.adapter.in.web;

import com.advancedlogin.application.auth.InvalidCredentialsException;
import com.advancedlogin.application.qr.QrSessionNotFoundException;
import com.advancedlogin.application.ratelimit.TooManyRequestsException;
import com.advancedlogin.domain.qr.IllegalQrTransitionException;
import com.advancedlogin.domain.qr.InvalidExchangeCodeException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Exceptions → RFC 7807 problem+json (the base class covers Spring MVC errors such as validation). */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problem.setTitle("Invalid credentials");
        return problem;
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> tooManyRequests(TooManyRequestsException e) {
        long seconds = Math.max(1, (long) Math.ceil(e.retryAfter().toMillis() / 1000.0));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setTitle("Too many requests");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(problem);
    }

    @ExceptionHandler(QrSessionNotFoundException.class)
    ProblemDetail qrSessionNotFound(QrSessionNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("QR code not found");
        return problem;
    }

    @ExceptionHandler(InvalidExchangeCodeException.class)
    ProblemDetail invalidExchangeCode(InvalidExchangeCodeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid exchange code");
        return problem;
    }

    @ExceptionHandler(IllegalQrTransitionException.class)
    ProblemDetail illegalQrTransition(IllegalQrTransitionException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("QR code can't do that now");
        return problem;
    }
}
