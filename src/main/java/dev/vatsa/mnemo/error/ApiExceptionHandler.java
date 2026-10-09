package dev.vatsa.mnemo.error;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 {@link ProblemDetail} JSON, so every error has the same shape.
 * Extending {@link ResponseEntityExceptionHandler} also covers Spring's own errors
 * (validation failures, unreadable JSON, ResponseStatusException) with ProblemDetail bodies.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem);
    }
}
