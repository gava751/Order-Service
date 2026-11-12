package com.innoboard.orderservice.client;

import com.innoboard.orderservice.dto.UserInfoResponse;
import com.innoboard.orderservice.exception.UserNotFoundException;
import com.innoboard.orderservice.exception.UserServiceUnavailableException;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

@Component
public class UserClient {

    private final RestClient userRestClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public UserClient(RestClient userRestClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.userRestClient = userRestClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public UserInfoResponse getUserById(Long id) {
        return runWithCircuitBreaker(() -> execute(
                () -> userRestClient.get()
                        .uri("/internal/users/{id}", id)
                        .retrieve()
                        .body(UserInfoResponse.class)));
    }

    public UserInfoResponse getUserByEmail(String email) {
        return runWithCircuitBreaker(() -> execute(
                () -> userRestClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/internal/users/by-email")
                                .queryParam("email", email)
                                .build())
                        .retrieve()
                        .body(UserInfoResponse.class)));
    }

    private UserInfoResponse runWithCircuitBreaker(Supplier<UserInfoResponse> call) {
        return circuitBreakerFactory.create(UserCircuitBreakerConfig.USER_SERVICE_CIRCUIT_BREAKER)
                .run(call, this::onFailure);
    }

    private UserInfoResponse execute(Supplier<UserInfoResponse> call) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new UserNotFoundException("User not found in User Service");
            }
            throw e;
        }
    }

    private UserInfoResponse onFailure(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof ExecutionException executionException && executionException.getCause() != null) {
            cause = executionException.getCause();
        }
        if (cause instanceof UserNotFoundException userNotFoundException) {
            throw userNotFoundException;
        }
        throw new UserServiceUnavailableException();
    }
}
