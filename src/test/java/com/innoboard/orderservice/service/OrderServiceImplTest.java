package com.innoboard.orderservice.service;

import com.innoboard.orderservice.client.UserClient;
import com.innoboard.orderservice.domain.ItemEntity;
import com.innoboard.orderservice.domain.OrderEntity;
import com.innoboard.orderservice.domain.OrderItemEntity;
import com.innoboard.orderservice.domain.OrderStatus;
import com.innoboard.orderservice.dto.CreateOrderRequest;
import com.innoboard.orderservice.dto.OrderItemRequest;
import com.innoboard.orderservice.dto.OrderResponse;
import com.innoboard.orderservice.dto.UpdateOrderRequest;
import com.innoboard.orderservice.dto.UserInfoResponse;
import com.innoboard.orderservice.exception.ItemNotFoundException;
import com.innoboard.orderservice.exception.OrderNotFoundException;
import com.innoboard.orderservice.exception.UserNotFoundException;
import com.innoboard.orderservice.mapper.OrderItemMapperImpl;
import com.innoboard.orderservice.mapper.OrderMapperImpl;
import com.innoboard.orderservice.repository.ItemRepository;
import com.innoboard.orderservice.repository.OrderRepository;
import com.innoboard.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl unit tests")
class OrderServiceImplTest {

    private static final String EMAIL = "ivan@example.com";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private UserClient userClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        OrderMapperImpl orderMapper = new OrderMapperImpl();
        ReflectionTestUtils.setField(orderMapper, "orderItemMapper", new OrderItemMapperImpl());
        orderService = new OrderServiceImpl(orderRepository, itemRepository, userClient,
                orderMapper, new OrderItemMapperImpl(), transactionTemplate);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
    }

    private UserInfoResponse user(Long id) {
        return new UserInfoResponse(id, "Ivan", "Ivanov", LocalDate.of(1990, 1, 1), EMAIL, true);
    }

    private ItemEntity item(Long id, String name, String price) {
        return ItemEntity.builder()
                .id(id)
                .name(name)
                .price(new BigDecimal(price))
                .build();
    }

    private OrderItemRequest itemRequest(Long itemId, int quantity) {
        return new OrderItemRequest(itemId, quantity);
    }

    private CreateOrderRequest createRequest() {
        return new CreateOrderRequest(EMAIL, List.of(itemRequest(1L, 2), itemRequest(2L, 3)));
    }

    private OrderEntity order(Long id, Long userId) {
        OrderItemEntity orderItem = OrderItemEntity.builder()
                .id(100L)
                .item(item(1L, "Phone", "100.00"))
                .quantity(2)
                .build();
        OrderEntity order = OrderEntity.builder()
                .id(id)
                .userId(userId)
                .status(OrderStatus.NEW)
                .totalPrice(new BigDecimal("200.00"))
                .build();
        order.addItem(orderItem);
        return order;
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("resolves user, links items, computes total and returns response with user info")
        void savesOrderWithResolvedUserAndComputedTotal() {
            when(userClient.getUserByEmail(EMAIL)).thenReturn(user(5L));
            when(itemRepository.findAllById(anySet())).thenReturn(List.of(
                    item(1L, "Phone", "100.00"),
                    item(2L, "Laptop", "50.00")));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            OrderResponse response = orderService.create(createRequest());

            ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
            verify(orderRepository).save(captor.capture());
            OrderEntity saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(5L);
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.NEW);
            assertThat(saved.getTotalPrice()).isEqualByComparingTo("350.00");
            assertThat(saved.getOrderItems()).hasSize(2);
            assertThat(saved.getOrderItems().get(0).getOrder()).isSameAs(saved);
            assertThat(saved.getOrderItems().get(0).getItem().getId()).isEqualTo(1L);
            assertThat(saved.getOrderItems().get(0).getQuantity()).isEqualTo(2);
            assertThat(response.user().id()).isEqualTo(5L);
            assertThat(response.items()).hasSize(2);
            assertThat(response.items().get(0).itemName()).isEqualTo("Phone");
            assertThat(response.items().get(0).price()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("unknown email propagates UserNotFoundException without touching repository")
        void unknownEmailPropagatesWithoutSaving() {
            when(userClient.getUserByEmail(EMAIL)).thenThrow(new UserNotFoundException("User not found"));

            assertThatThrownBy(() -> orderService.create(createRequest()))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(itemRepository, orderRepository);
        }

        @Test
        @DisplayName("missing item ids throw ItemNotFoundException and nothing is saved")
        void missingItemsThrowAndNothingIsSaved() {
            when(userClient.getUserByEmail(EMAIL)).thenReturn(user(5L));
            when(itemRepository.findAllById(anySet())).thenReturn(List.of(item(1L, "Phone", "100.00")));

            assertThatThrownBy(() -> orderService.create(createRequest()))
                    .isInstanceOf(ItemNotFoundException.class);

            verify(orderRepository, never()).save(any(OrderEntity.class));
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns order enriched with user info")
        void returnsEnrichedOrder() {
            when(orderRepository.findWithItemsByIdAndDeletedFalse(10L)).thenReturn(Optional.of(order(10L, 5L)));
            when(userClient.getUserById(5L)).thenReturn(user(5L));

            OrderResponse response = orderService.getById(10L);

            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.status()).isEqualTo(OrderStatus.NEW);
            assertThat(response.user().id()).isEqualTo(5L);
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).itemName()).isEqualTo("Phone");
        }

        @Test
        @DisplayName("unknown order throws OrderNotFoundException without user lookup")
        void unknownOrderThrowsWithoutUserLookup() {
            when(orderRepository.findWithItemsByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getById(99L))
                    .isInstanceOf(OrderNotFoundException.class);

            verifyNoInteractions(userClient);
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("loads user info once per unique user id")
        void loadsUserInfoOncePerUniqueUser() {
            OrderEntity first = order(1L, 5L);
            OrderEntity second = order(2L, 5L);
            OrderEntity third = order(3L, 7L);
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(first, second, third), PageRequest.of(0, 10), 3));
            when(orderRepository.findAllWithItemsByIdIn(anyCollection())).thenReturn(List.of(first, second, third));
            when(userClient.getUserById(5L)).thenReturn(user(5L));
            when(userClient.getUserById(7L)).thenReturn(user(7L));

            var page = orderService.getAll(null, null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getContent().get(2).user().id()).isEqualTo(7L);
            verify(userClient, times(2)).getUserById(anyLong());
        }
    }

    @Nested
    @DisplayName("getAllByUserId")
    class GetAllByUserId {

        @Test
        @DisplayName("resolves user first and returns all orders with the same user info")
        void resolvesUserFirstAndReturnsOrders() {
            when(userClient.getUserById(5L)).thenReturn(user(5L));
            when(orderRepository.findAllWithItemsByUserIdAndDeletedFalse(5L))
                    .thenReturn(List.of(order(1L, 5L), order(2L, 5L)));

            List<OrderResponse> orders = orderService.getAllByUserId(5L);

            assertThat(orders).hasSize(2);
            assertThat(orders.get(0).user().id()).isEqualTo(5L);
            assertThat(orders.get(1).user().id()).isEqualTo(5L);
        }

        @Test
        @DisplayName("nonexistent user propagates 404 without repository query")
        void nonexistentUserPropagatesWithoutRepositoryQuery() {
            when(userClient.getUserById(5L)).thenThrow(new UserNotFoundException("User not found"));

            assertThatThrownBy(() -> orderService.getAllByUserId(5L))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(orderRepository);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("replaces items, recalculates total and returns enriched response")
        void replacesItemsAndRecalculatesTotal() {
            when(orderRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(order(10L, 5L)));
            when(itemRepository.findAllById(anySet())).thenReturn(List.of(item(2L, "Laptop", "50.00")));
            when(userClient.getUserById(5L)).thenReturn(user(5L));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            OrderResponse response = orderService.update(10L,
                    new UpdateOrderRequest(List.of(itemRequest(2L, 3))));

            ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
            verify(orderRepository).save(captor.capture());
            OrderEntity saved = captor.getValue();
            assertThat(saved.getOrderItems()).hasSize(1);
            assertThat(saved.getOrderItems().get(0).getItem().getId()).isEqualTo(2L);
            assertThat(saved.getOrderItems().get(0).getOrder()).isSameAs(saved);
            assertThat(saved.getTotalPrice()).isEqualByComparingTo("150.00");
            assertThat(response.user().id()).isEqualTo(5L);
        }

        @Test
        @DisplayName("unknown order throws OrderNotFoundException without saving")
        void unknownOrderThrowsWithoutSaving() {
            when(itemRepository.findAllById(anySet())).thenReturn(List.of(item(1L, "Phone", "100.00")));
            when(orderRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.update(99L,
                    new UpdateOrderRequest(List.of(itemRequest(1L, 1)))))
                    .isInstanceOf(OrderNotFoundException.class);

            verify(orderRepository, never()).save(any(OrderEntity.class));
            verifyNoInteractions(userClient);
        }

        @Test
        @DisplayName("missing item throws ItemNotFoundException before any order modification")
        void missingItemThrowsBeforeOrderModification() {
            OrderEntity existing = order(10L, 5L);
            when(itemRepository.findAllById(anySet())).thenReturn(List.of(item(1L, "Phone", "100.00")));

            assertThatThrownBy(() -> orderService.update(10L,
                    new UpdateOrderRequest(List.of(itemRequest(2L, 1)))))
                    .isInstanceOf(ItemNotFoundException.class);

            verify(orderRepository, never()).save(any(OrderEntity.class));
            assertThat(existing.getOrderItems()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("marks order as deleted and saves")
        void marksOrderAsDeleted() {
            OrderEntity existing = order(10L, 5L);
            when(orderRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));

            orderService.delete(10L);

            ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getDeleted()).isTrue();
        }

        @Test
        @DisplayName("unknown order throws OrderNotFoundException without saving")
        void unknownOrderThrowsWithoutSaving() {
            when(orderRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.delete(99L))
                    .isInstanceOf(OrderNotFoundException.class);

            verify(orderRepository, never()).save(any(OrderEntity.class));
        }
    }
}
