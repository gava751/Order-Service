package com.innoboard.orderservice.service;

import com.innoboard.orderservice.dto.CreateOrderRequest;
import com.innoboard.orderservice.dto.OrderResponse;
import com.innoboard.orderservice.dto.UpdateOrderRequest;
import com.innoboard.orderservice.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface OrderService {

    OrderResponse create(CreateOrderRequest request);

    OrderResponse getById(Long id);

    Page<OrderResponse> getAll(Instant createdFrom, Instant createdTo,
                               Set<OrderStatus> statuses, Pageable pageable);

    List<OrderResponse> getAllByUserId(Long userId);

    OrderResponse update(Long id, UpdateOrderRequest request);

    void delete(Long id);
}
