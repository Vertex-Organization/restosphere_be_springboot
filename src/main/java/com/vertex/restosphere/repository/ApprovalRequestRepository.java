package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.ApprovalRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    boolean existsByTypeAndTargetIdAndStatus(ApprovalRequest.Type type, UUID targetId, ApprovalRequest.Status status);
}
