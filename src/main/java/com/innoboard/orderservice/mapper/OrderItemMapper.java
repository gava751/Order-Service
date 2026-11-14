package com.innoboard.orderservice.mapper;

import com.innoboard.orderservice.domain.OrderItemEntity;
import com.innoboard.orderservice.dto.OrderItemRequest;
import com.innoboard.orderservice.dto.OrderItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "item", ignore = true)
    OrderItemEntity toEntity(OrderItemRequest request);

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "itemName", source = "item.name")
    @Mapping(target = "price", source = "item.price")
    OrderItemResponse toResponse(OrderItemEntity entity);
}
