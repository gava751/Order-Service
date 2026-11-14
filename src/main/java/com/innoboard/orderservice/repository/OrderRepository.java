package com.innoboard.orderservice.repository;

import com.innoboard.orderservice.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByIdAndDeletedFalse(Long id);

    @Query("select o from OrderEntity o left join fetch o.orderItems oi left join fetch oi.item "
            + "where o.id = :id and o.deleted = false")
    Optional<OrderEntity> findWithItemsByIdAndDeletedFalse(@Param("id") Long id);

    @Query("select distinct o from OrderEntity o left join fetch o.orderItems oi left join fetch oi.item "
            + "where o.userId = :userId and o.deleted = false")
    List<OrderEntity> findAllWithItemsByUserIdAndDeletedFalse(@Param("userId") Long userId);

    @Query("select distinct o from OrderEntity o left join fetch o.orderItems oi left join fetch oi.item "
            + "where o.id in :ids and o.deleted = false")
    List<OrderEntity> findAllWithItemsByIdIn(@Param("ids") Collection<Long> ids);
}
