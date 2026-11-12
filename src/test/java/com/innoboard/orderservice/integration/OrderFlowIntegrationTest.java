package com.innoboard.orderservice.integration;

import com.innoboard.orderservice.domain.ItemEntity;
import com.innoboard.orderservice.domain.OrderEntity;
import com.innoboard.orderservice.domain.OrderStatus;
import com.innoboard.orderservice.dto.CreateOrderRequest;
import com.innoboard.orderservice.dto.OrderItemRequest;
import com.innoboard.orderservice.dto.OrderResponse;
import com.innoboard.orderservice.dto.UpdateOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Order API end-to-end")
class OrderFlowIntegrationTest extends IntegrationTestBase {

    private List<ItemEntity> seededItems;

    @BeforeEach
    void seedItems() {
        seededItems = itemRepository.saveAll(List.of(
                ItemEntity.builder().name("Phone").price(new BigDecimal("100.00")).build(),
                ItemEntity.builder().name("Laptop").price(new BigDecimal("150.00")).build()));
    }

    private List<OrderItemRequest> itemRequests() {
        return List.of(
                new OrderItemRequest(seededItems.get(0).getId(), 2),
                new OrderItemRequest(seededItems.get(1).getId(), 1));
    }

    private OrderResponse createDefaultOrder() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateOrderRequest("ivan@example.com", itemRequests()));
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), OrderResponse.class);
    }

    private OrderResponse getOrder(Long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), OrderResponse.class);
    }

    private void markStatus(Long id, OrderStatus status) {
        OrderEntity order = orderRepository.findById(id).orElseThrow();
        order.setStatus(status);
        orderRepository.save(order);
    }

    @Test
    @DisplayName("create returns 201 with user info, items and computed total")
    void createOrderReturnsCreatedOrderWithUserAndItems() throws Exception {
        OrderResponse response = createDefaultOrder();

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.NEW);
        assertThat(response.totalPrice()).isEqualByComparingTo("350.00");
        assertThat(response.user().id()).isEqualTo(1L);
        assertThat(response.user().email()).isEqualTo("ivan@example.com");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).itemId()).isEqualTo(seededItems.get(0).getId());
        assertThat(response.items().get(0).itemName()).isEqualTo("Phone");
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("create with unknown email returns 404")
    void createOrderWithUnknownEmailReturnsNotFound() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateOrderRequest("unknown@example.com", itemRequests()));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("create with missing items returns 404")
    void createOrderWithMissingItemsReturnsNotFound() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateOrderRequest("ivan@example.com",
                List.of(new OrderItemRequest(999_999L, 1))));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Items not found: [999999]"));
    }

    @Test
    @DisplayName("create with invalid body returns 400 with field errors")
    void createOrderWithInvalidBodyReturnsBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateOrderRequest("not-an-email", List.of()));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userEmail").exists())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    @Test
    @DisplayName("create with non-positive quantity returns 400")
    void createOrderWithZeroQuantityReturnsBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateOrderRequest("ivan@example.com",
                List.of(new OrderItemRequest(seededItems.get(0).getId(), 0))));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("items[0].quantity")));
    }

    @Test
    @DisplayName("get by id returns order with user info")
    void getOrderByIdReturnsOrder() throws Exception {
        OrderResponse created = createDefaultOrder();

        OrderResponse response = getOrder(created.id());

        assertThat(response.id()).isEqualTo(created.id());
        assertThat(response.status()).isEqualTo(OrderStatus.NEW);
        assertThat(response.user().id()).isEqualTo(1L);
        assertThat(response.items()).hasSize(2);
    }

    @Test
    @DisplayName("get unknown order returns 404")
    void getUnknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("update replaces items and recalculates total")
    void updateOrderReplacesItemsAndRecalculatesTotal() throws Exception {
        OrderResponse created = createDefaultOrder();
        String body = objectMapper.writeValueAsString(new UpdateOrderRequest(
                List.of(new OrderItemRequest(seededItems.get(1).getId(), 3))));

        MvcResult result = mockMvc.perform(put("/api/orders/" + created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        OrderResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), OrderResponse.class);
        assertThat(updated.items()).hasSize(1);
        assertThat(updated.items().get(0).itemId()).isEqualTo(seededItems.get(1).getId());
        assertThat(updated.totalPrice()).isEqualByComparingTo("450.00");
    }

    @Test
    @DisplayName("delete hides order from reads but keeps soft-deleted row")
    void deleteOrderSoftDeletes() throws Exception {
        OrderResponse created = createDefaultOrder();

        mockMvc.perform(delete("/api/orders/" + created.id()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/orders/" + created.id()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        OrderEntity softDeleted = orderRepository.findById(created.id()).orElseThrow();
        assertThat(softDeleted.getDeleted()).isTrue();
    }

    @Test
    @DisplayName("get all filters by status")
    void getAllFiltersByStatus() throws Exception {
        OrderResponse first = createDefaultOrder();
        OrderResponse second = createDefaultOrder();
        markStatus(first.id(), OrderStatus.PAID);

        mockMvc.perform(get("/api/orders").param("statuses", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first.id().intValue()));
        mockMvc.perform(get("/api/orders").param("statuses", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second.id().intValue()));
    }

    @Test
    @DisplayName("get all filters by creation date range")
    void getAllFiltersByCreationDateRange() throws Exception {
        createDefaultOrder();

        mockMvc.perform(get("/api/orders")
                        .param("createdFrom", "2000-01-01T00:00:00Z")
                        .param("createdTo", "2100-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/orders").param("createdFrom", "2100-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("get all paginates")
    void getAllPaginates() throws Exception {
        createDefaultOrder();
        createDefaultOrder();
        createDefaultOrder();

        mockMvc.perform(get("/api/orders").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/orders").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("get all with invalid sort returns 400")
    void getAllWithInvalidSortReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/orders").param("sort", "notAField"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("get orders by user id returns list with user info")
    void getOrdersByUserIdReturnsOrders() throws Exception {
        createDefaultOrder();
        createDefaultOrder();

        mockMvc.perform(get("/api/orders/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].user.id").value(1));
    }

    @Test
    @DisplayName("get orders by nonexistent user returns 404")
    void getOrdersByNonexistentUserReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/user/999"))
                .andExpect(status().isNotFound());
    }
}
