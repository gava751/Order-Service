package com.innoboard.orderservice.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record ValidationErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        Map<String, String> errors
) {

    public static ValidationErrorResponse of(HttpStatus status, Map<String, String> errors) {
        return new ValidationErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), errors);
    }
}
