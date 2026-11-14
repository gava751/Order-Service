package com.innoboard.orderservice.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("Order with id=%d not found".formatted(id));
    }
}
