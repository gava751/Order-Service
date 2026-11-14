package com.innoboard.orderservice.client;

import com.innoboard.orderservice.exception.UserNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class UserCircuitBreakerConfig {

    public static final String USER_SERVICE_CIRCUIT_BREAKER = "userService";

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> userServiceCircuitBreakerCustomizer() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreExceptions(UserNotFoundException.class)
                .build();
        return factory -> factory.configure(
                builder -> builder.circuitBreakerConfig(config),
                USER_SERVICE_CIRCUIT_BREAKER);
    }
}
