package com.innoboard.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull Long itemId,
        @NotNull @Positive Integer quantity
) {
}
