package com.synapse.backend.shared;

import java.util.Comparator;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.synapse.backend.shared.exceptions.BadGatewayException;
import com.synapse.backend.shared.exceptions.BadRequestException;
import com.synapse.backend.shared.exceptions.ConflictException;
import com.synapse.backend.shared.exceptions.NotFoundException;
import com.synapse.backend.shared.exceptions.TooManyRequestsException;
import com.synapse.backend.shared.exceptions.UnauthorisedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorisedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorised(UnauthorisedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BadGatewayException.class)
    public ResponseEntity<ErrorResponse> handleBadGateway(BadGatewayException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidationErrors(HandlerMethodValidationException ex) {
        String message = ex.getParameterValidationResults()
                .stream()
                .flatMap(result -> result
                        .getResolvableErrors()
                        .stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .findFirst()
                .orElse("Invalid request");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .min(Comparator.comparingInt(GlobalExceptionHandler::severity).thenComparing(FieldError::getField))
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(message));
    }

    /**
     * Orders the constraints that can fail on one value, lowest first.
     *
     * <p>A body reports one message, so when several constraints on a field fail at once
     * something has to choose between them. Left to stream order that choice is arbitrary,
     * and a blank name could be reported as blank on one request and as too short on the
     * next. Reporting the most basic failure first means a client is told what is most wrong:
     * that a value is missing before it is told the value is the wrong length, and its length
     * before its format.</p>
     *
     * @param error one failed constraint.
     * @return the rank to sort it by.
     */
    private static int severity(FieldError error) {
        return switch (error.getCode() == null ? "" : error.getCode()) {
            case "NotNull", "NotBlank", "NotEmpty", "NullOrNotBlank" -> 0;
            case "Size", "Min", "Max", "MaxUtf8Bytes" -> 1;
            default -> 2;
        };
    }
}
