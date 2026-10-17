package com.advancedlogin.adapter.in.web;

import com.advancedlogin.application.auth.InvalidCredentialsException;
import com.advancedlogin.application.qr.QrSessionNotFoundException;
import com.advancedlogin.domain.qr.IllegalQrTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps application exceptions to RFC 7807 problem responses ({@code application/problem+json}).
 * Extending ResponseEntityExceptionHandler also turns Spring MVC errors (e.g. validation
 * failures) into problem responses.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problem.setTitle("Invalid credentials");
        return problem;
    }

    @ExceptionHandler(QrSessionNotFoundException.class)
    ProblemDetail qrSessionNotFound(QrSessionNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("QR code not found");
        return problem;
    }

    @ExceptionHandler(IllegalQrTransitionException.class)
    ProblemDetail illegalQrTransition(IllegalQrTransitionException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("QR code can't do that now");
        return problem;
    }
}
