package com.innoboard.orderservice.dto;

import com.innoboard.orderservice.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalPrice,
        UserInfoResponse user,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
