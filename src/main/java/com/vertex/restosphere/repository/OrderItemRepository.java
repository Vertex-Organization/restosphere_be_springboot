package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.TableSession;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    boolean existsByOrderSessionAndStatusIn(TableSession session, Collection<OrderItem.Status> statuses);
}
