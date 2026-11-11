package com.innoboard.orderservice.repository;

import com.innoboard.orderservice.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByIdAndDeletedFalse(Long id);

    List<OrderEntity> findAllByUserIdAndDeletedFalse(Long userId);
}
