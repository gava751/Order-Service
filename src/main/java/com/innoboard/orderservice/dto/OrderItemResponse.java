package com.innoboard.orderservice.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long itemId,
        String itemName,
        BigDecimal price,
        Integer quantity
) {
}
