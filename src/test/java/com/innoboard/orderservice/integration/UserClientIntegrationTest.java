package com.innoboard.orderservice.integration;

import com.innoboard.orderservice.client.UserClient;
import com.innoboard.orderservice.client.UserClientConfig;
import com.innoboard.orderservice.client.UserCircuitBreakerConfig;
import com.innoboard.orderservice.dto.UserInfoResponse;
import com.innoboard.orderservice.exception.UserNotFoundException;
import com.innoboard.orderservice.exception.UserServiceUnavailableException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4jBulkheadProvider;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserClient over real HTTP with circuit breaker")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserClientIntegrationTest {

    private static final long USER_ID = 1L;
    private static final long NOT_FOUND_USER_ID = 2L;
    private static final long SERVER_ERROR_USER_ID = 3L;

    private static MockWebServer userServiceMock;
    private static Resilience4JCircuitBreakerFactory circuitBreakerFactory;
    private static UserClient userClient;

    @BeforeAll
    static void setUp() throws IOException {
        userServiceMock = new MockWebServer();
        userServiceMock.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.matches("/internal/users/\\d+")) {
                    long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
                    if (id == NOT_FOUND_USER_ID) {
                        return new MockResponse().setResponseCode(404);
                    }
                    if (id == SERVER_ERROR_USER_ID) {
                        return new MockResponse().setResponseCode(500);
                    }
                    return user(id);
                }
                if (path != null && path.startsWith("/internal/users/by-email")) {
                    return user(USER_ID);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        userServiceMock.start();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + userServiceMock.getPort())
                .defaultHeader(UserClientConfig.INTERNAL_TOKEN_HEADER, "test-internal-token")
                .requestFactory(requestFactory)
                .build();

        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        properties.setDisableTimeLimiter(true);
        circuitBreakerFactory = new Resilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(),
                new Resilience4jBulkheadProvider(
                        ThreadPoolBulkheadRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(), properties));
        circuitBreakerFactory.configure(
                builder -> builder.circuitBreakerConfig(circuitBreakerConfig()),
                UserCircuitBreakerConfig.USER_SERVICE_CIRCUIT_BREAKER);
        userClient = new UserClient(restClient, circuitBreakerFactory);
    }

    private static CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreExceptions(UserNotFoundException.class)
                .build();
    }

    private static MockResponse user(long id) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":%d,"name":"Ivan","surname":"Ivanov","birthDate":"1990-01-01","email":"ivan@example.com","active":true}
                        """.formatted(id));
    }

    @AfterAll
    static void tearDown() throws IOException {
        userServiceMock.shutdown();
    }

    @Test
    @Order(1)
    @DisplayName("getUserById parses response and sends internal token")
    void getUserByIdParsesResponseAndSendsInternalToken() throws Exception {
        UserInfoResponse response = userClient.getUserById(USER_ID);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Ivan");
        RecordedRequest recorded = userServiceMock.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/internal/users/1");
        assertThat(recorded.getHeader(UserClientConfig.INTERNAL_TOKEN_HEADER)).isEqualTo("test-internal-token");
    }

    @Test
    @Order(2)
    @DisplayName("getUserByEmail builds correct uri with query parameter")
    void getUserByEmailBuildsCorrectUri() throws Exception {
        UserInfoResponse response = userClient.getUserByEmail("ivan@example.com");

        assertThat(response.id()).isEqualTo(1L);
        RecordedRequest recorded = userServiceMock.takeRequest();
        assertThat(recorded.getPath()).startsWith("/internal/users/by-email?");
        assertThat(recorded.getPath()).contains("email=ivan");
    }

    @Test
    @Order(3)
    @DisplayName("404 from user service throws UserNotFoundException")
    void getUserByIdNotFoundThrowsUserNotFoundException() {
        assertThatThrownBy(() -> userClient.getUserById(NOT_FOUND_USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @Order(4)
    @DisplayName("500 from user service throws UserServiceUnavailableException")
    void getUserByIdServerErrorThrowsUserServiceUnavailableException() {
        assertThatThrownBy(() -> userClient.getUserById(SERVER_ERROR_USER_ID))
                .isInstanceOf(UserServiceUnavailableException.class);
    }

    @Test
    @Order(5)
    @DisplayName("repeated 404 responses do not open the circuit breaker")
    void userNotFoundDoesNotOpenCircuitBreaker() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> userClient.getUserById(NOT_FOUND_USER_ID))
                    .isInstanceOf(UserNotFoundException.class);
        }

        UserInfoResponse response = userClient.getUserById(USER_ID);
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    @Order(6)
    @DisplayName("circuit opens after repeated failures and fails fast without network attempts")
    void circuitOpensAfterRepeatedFailuresAndFailsFast() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> userClient.getUserById(SERVER_ERROR_USER_ID))
                    .isInstanceOf(UserServiceUnavailableException.class);
        }
        int requestsAfterFailures = userServiceMock.getRequestCount();

        assertThatThrownBy(() -> userClient.getUserById(SERVER_ERROR_USER_ID))
                .isInstanceOf(UserServiceUnavailableException.class);
        assertThat(userServiceMock.getRequestCount()).isEqualTo(requestsAfterFailures);

        circuitBreakerFactory.getCircuitBreakerRegistry()
                .circuitBreaker(UserCircuitBreakerConfig.USER_SERVICE_CIRCUIT_BREAKER)
                .transitionToClosedState();
    }
}
