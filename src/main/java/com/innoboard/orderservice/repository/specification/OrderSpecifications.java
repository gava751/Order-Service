package com.innoboard.orderservice.repository.specification;

import com.innoboard.orderservice.domain.OrderEntity;
import com.innoboard.orderservice.domain.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<OrderEntity> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<OrderEntity> hasStatusIn(Collection<OrderStatus> statuses) {
        return (root, query, cb) ->
                statuses == null || statuses.isEmpty()
                        ? cb.conjunction()
                        : root.<OrderStatus>get("status").in(statuses);
    }

    public static Specification<OrderEntity> createdAfter(Instant from) {
        return (root, query, cb) ->
                from == null
                        ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from);
    }

    public static Specification<OrderEntity> createdBefore(Instant to) {
        return (root, query, cb) ->
                to == null
                        ? cb.conjunction()
                        : cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), to);
    }
}
