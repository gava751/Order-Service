package com.innoboard.orderservice.dto;

import java.time.LocalDate;

public record UserInfoResponse(
        Long id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        Boolean active
) {
}
