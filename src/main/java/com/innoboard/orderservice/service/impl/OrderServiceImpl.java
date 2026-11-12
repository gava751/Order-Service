package com.innoboard.orderservice.service.impl;

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
import com.innoboard.orderservice.mapper.OrderItemMapper;
import com.innoboard.orderservice.mapper.OrderMapper;
import com.innoboard.orderservice.repository.ItemRepository;
import com.innoboard.orderservice.repository.OrderRepository;
import com.innoboard.orderservice.repository.specification.OrderSpecifications;
import com.innoboard.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final UserClient userClient;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        UserInfoResponse user = userClient.getUserByEmail(request.userEmail());
        Map<Long, ItemEntity> itemsById = loadItems(request.items());
        OrderEntity order = orderMapper.toEntity(request);
        order.setUserId(user.id());
        order.setStatus(OrderStatus.NEW);
        order.setTotalPrice(calculateTotalPrice(request.items(), itemsById));
        for (OrderItemRequest itemRequest : request.items()) {
            OrderItemEntity orderItem = orderItemMapper.toEntity(itemRequest);
            orderItem.setItem(itemsById.get(itemRequest.itemId()));
            order.addItem(orderItem);
        }
        orderRepository.save(order);
        return orderMapper.toResponse(order, user);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        OrderEntity order = findOrderOrThrow(id);
        return orderMapper.toResponse(order, userClient.getUserById(order.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAll(Instant createdFrom, Instant createdTo,
                                      Set<OrderStatus> statuses, Pageable pageable) {
        Specification<OrderEntity> spec = OrderSpecifications.notDeleted()
                .and(OrderSpecifications.createdAfter(createdFrom))
                .and(OrderSpecifications.createdBefore(createdTo))
                .and(OrderSpecifications.hasStatusIn(statuses));
        Page<OrderEntity> page = orderRepository.findAll(spec, pageable);
        Map<Long, UserInfoResponse> usersById = loadUsers(page.map(OrderEntity::getUserId).toSet());
        return page.map(order -> orderMapper.toResponse(order, usersById.get(order.getUserId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllByUserId(Long userId) {
        UserInfoResponse user = userClient.getUserById(userId);
        return orderRepository.findAllByUserIdAndDeletedFalse(userId).stream()
                .map(order -> orderMapper.toResponse(order, user))
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse update(Long id, UpdateOrderRequest request) {
        OrderEntity order = findOrderOrThrow(id);
        Map<Long, ItemEntity> itemsById = loadItems(request.items());
        order.getOrderItems().clear();
        order.setTotalPrice(calculateTotalPrice(request.items(), itemsById));
        for (OrderItemRequest itemRequest : request.items()) {
            OrderItemEntity orderItem = orderItemMapper.toEntity(itemRequest);
            orderItem.setItem(itemsById.get(itemRequest.itemId()));
            order.addItem(orderItem);
        }
        orderRepository.save(order);
        return orderMapper.toResponse(order, userClient.getUserById(order.getUserId()));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        OrderEntity order = findOrderOrThrow(id);
        order.setDeleted(true);
        orderRepository.save(order);
    }

    private OrderEntity findOrderOrThrow(Long id) {
        return orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private Map<Long, ItemEntity> loadItems(List<OrderItemRequest> itemRequests) {
        Set<Long> itemIds = itemRequests.stream()
                .map(OrderItemRequest::itemId)
                .collect(Collectors.toSet());
        Map<Long, ItemEntity> itemsById = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(ItemEntity::getId, Function.identity()));
        Set<Long> missingIds = itemIds.stream()
                .filter(itemId -> !itemsById.containsKey(itemId))
                .collect(Collectors.toSet());
        if (!missingIds.isEmpty()) {
            throw new ItemNotFoundException("Items not found: " + missingIds);
        }
        return itemsById;
    }

    private BigDecimal calculateTotalPrice(List<OrderItemRequest> itemRequests,
                                           Map<Long, ItemEntity> itemsById) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : itemRequests) {
            BigDecimal price = itemsById.get(itemRequest.itemId()).getPrice();
            totalPrice = totalPrice.add(price.multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }
        return totalPrice;
    }

    private Map<Long, UserInfoResponse> loadUsers(Set<Long> userIds) {
        Map<Long, UserInfoResponse> usersById = new LinkedHashMap<>();
        for (Long userId : userIds) {
            usersById.put(userId, userClient.getUserById(userId));
        }
        return usersById;
    }
}
