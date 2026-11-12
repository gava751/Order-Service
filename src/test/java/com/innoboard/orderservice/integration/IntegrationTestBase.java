package com.innoboard.orderservice.integration;

import com.innoboard.orderservice.repository.ItemRepository;
import com.innoboard.orderservice.repository.OrderRepository;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    static final MockWebServer userServiceMock = new MockWebServer();

    static {
        postgres.start();
        userServiceMock.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/internal/users/by-email") && !path.contains("unknown")) {
                    return user("1", "ivan@example.com");
                }
                if (path != null && path.matches("/internal/users/\\d+")) {
                    String id = path.substring(path.lastIndexOf('/') + 1);
                    if ("999".equals(id)) {
                        return notFound();
                    }
                    return user(id, "user%s@example.com".formatted(id));
                }
                return notFound();
            }
        });
        try {
            userServiceMock.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start User Service mock", e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.services.user-url", () -> "http://localhost:" + userServiceMock.getPort());
        registry.add("app.internal.token", () -> "test-internal-token");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected ItemRepository itemRepository;

    @BeforeEach
    void cleanState() {
        orderRepository.deleteAll();
        itemRepository.deleteAll();
    }

    private static MockResponse user(String id, String email) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":%s,"name":"Ivan","surname":"Ivanov","birthDate":"1990-01-01","email":"%s","active":true}
                        """.formatted(id, email));
    }

    private static MockResponse notFound() {
        return new MockResponse().setResponseCode(404);
    }
}
