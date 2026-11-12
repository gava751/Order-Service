package com.innoboard.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items
) {
}
