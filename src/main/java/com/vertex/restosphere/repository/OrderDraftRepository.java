package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.OrderDraft;
import com.vertex.restosphere.entity.TableSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDraftRepository extends JpaRepository<OrderDraft, UUID> {

    boolean existsByMenuItemId(UUID menuItemId);

    List<OrderDraft> findBySessionAndWaiterOrderByCreatedAt(TableSession session, Employee waiter);

    boolean existsBySession(TableSession session);
}
