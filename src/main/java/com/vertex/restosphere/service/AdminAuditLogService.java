package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.response.AuditLogResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AdminAuditLogService {

    List<AuditLogResponse> list(UUID actorId, String action, String actor, LocalDate from, LocalDate to,
            Integer limit);
}
