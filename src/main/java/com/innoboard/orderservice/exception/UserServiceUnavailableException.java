package com.innoboard.orderservice.exception;

public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException() {
        super("User Service is unavailable");
    }
}
