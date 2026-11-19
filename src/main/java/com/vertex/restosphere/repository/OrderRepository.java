package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.TableSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("select coalesce(max(o.roundNumber), 0) from Order o where o.session = :session")
    int findLastRoundNumber(TableSession session);
}
