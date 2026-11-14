package com.innoboard.orderservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(ItemNotFoundException.class)
    ResponseEntity<ErrorResponse> handleItemNotFound(ItemNotFoundException ex, HttpServletRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    ResponseEntity<ErrorResponse> handleUserServiceUnavailable(UserServiceUnavailableException ex,
                                                              HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                      HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}",
                request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, "Resource violates a data constraint",
                        request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                              HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String message = ex.getReason() == null ? "Request failed" : ex.getReason();
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ErrorResponse.of(ex.getStatusCode(), message, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() == null
                                ? "invalid value"
                                : fieldError.getDefaultMessage(),
                        (existing, duplicate) -> existing));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ValidationErrorResponse.of(HttpStatus.BAD_REQUEST, errors));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex,
                                                            HttpServletRequest request) {
        return badRequest(ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                       HttpServletRequest request) {
        return badRequest("Malformed request body", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                     HttpServletRequest request) {
        return badRequest("Invalid value for parameter '%s'".formatted(ex.getName()), request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ErrorResponse> handlePropertyReference(PropertyReferenceException ex,
                                                          HttpServletRequest request) {
        return badRequest("Invalid sort or filter parameter '%s'".formatted(ex.getPropertyName()), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                           HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "No such resource", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}: ", request.getRequestURI(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                        request.getRequestURI()));
    }

    private ResponseEntity<ErrorResponse> notFound(String message, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, message, request.getRequestURI()));
    }

    private ResponseEntity<ErrorResponse> badRequest(String message, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message, request.getRequestURI()));
    }
}
