package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Bill;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    boolean existsByStatus(Bill.Status status);
}
