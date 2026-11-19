package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.OrderDraft;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDraftRepository extends JpaRepository<OrderDraft, UUID> {

    boolean existsByMenuItemId(UUID menuItemId);
}
