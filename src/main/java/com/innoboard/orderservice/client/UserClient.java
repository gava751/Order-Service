package com.innoboard.orderservice.client;

import com.innoboard.orderservice.dto.UserInfoResponse;
import com.innoboard.orderservice.exception.UserNotFoundException;
import com.innoboard.orderservice.exception.UserServiceUnavailableException;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
        return runWithCircuitBreaker(() -> userRestClient.get()
                .uri("/internal/users/{id}", id)
                .retrieve()
                .body(UserInfoResponse.class));
    }

    public UserInfoResponse getUserByEmail(String email) {
        return runWithCircuitBreaker(() -> userRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/users/by-email")
                        .queryParam("email", email)
                        .build())
                .retrieve()
                .body(UserInfoResponse.class));
    }

    private UserInfoResponse runWithCircuitBreaker(Supplier<UserInfoResponse> call) {
        return circuitBreakerFactory.create(UserCircuitBreakerConfig.USER_SERVICE_CIRCUIT_BREAKER)
                .run(call, this::onFailure);
    }

    private UserInfoResponse onFailure(Throwable throwable) {
        if (throwable instanceof RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new UserNotFoundException("User not found in User Service");
            }
            throw new UserServiceUnavailableException();
        }
        if (throwable instanceof RuntimeException e) {
            throw e;
        }
        throw new UserServiceUnavailableException();
    }
}
