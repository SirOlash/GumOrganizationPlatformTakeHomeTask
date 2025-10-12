package com.Semicolon.exceptions;

import com.Semicolon.api.ErrorEnvelope;
import com.Semicolon.api.ErrorPayload;
import com.Semicolon.api.FieldDetail;
import com.Semicolon.exceptions.api.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorEnvelope> handleApi(ApiException ex) {
        String trace = "req_" + UUID.randomUUID();

        ErrorPayload payload = new ErrorPayload(
                ex.getCode(),
                ex.getMessage(),
                ex.getDetails(),
                trace
        );
        return ResponseEntity.status(ex.getStatus()).body(new ErrorEnvelope(payload));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldDetail> details = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> new FieldDetail(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());

        String trace = "req_" + UUID.randomUUID().toString();
        ErrorPayload payload = new ErrorPayload("VALIDATION_ERROR", "Validation failed", details, trace);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorEnvelope(payload));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldDetail> details = ex.getConstraintViolations().stream()
                .map(cv -> new FieldDetail(cv.getPropertyPath().toString(), cv.getMessage()))
                .collect(Collectors.toList());

        String trace = "req_" + UUID.randomUUID().toString();
        ErrorPayload payload = new ErrorPayload("VALIDATION_ERROR", "Validation failed", details, trace);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorEnvelope(payload));
    }

    // Malformed JSON / unreadable body -> 422
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidJson(HttpMessageNotReadableException ex) {
        List<FieldDetail> details = List.of(new FieldDetail("body", "Malformed JSON or wrong types"));
        String trace = "req_" + UUID.randomUUID().toString();
        ErrorPayload payload = new ErrorPayload("VALIDATION_ERROR", "Invalid request payload", details, trace);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorEnvelope(payload));
    }

}

