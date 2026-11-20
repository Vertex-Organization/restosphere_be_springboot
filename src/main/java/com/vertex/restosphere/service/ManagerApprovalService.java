package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.ApprovalDecisionRequest;
import com.vertex.restosphere.dto.response.ApprovalResponse;
import com.vertex.restosphere.entity.ApprovalRequest;
import java.util.List;
import java.util.UUID;

public interface ManagerApprovalService {

    List<ApprovalResponse> list(UUID actorId, ApprovalRequest.Status status, ApprovalRequest.Type type);

    ApprovalResponse decide(UUID actorId, UUID id, ApprovalDecisionRequest req);
}
