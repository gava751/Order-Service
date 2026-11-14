package com.innoboard.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Email String userEmail,
        @NotEmpty @Valid List<OrderItemRequest> items
) {
}
