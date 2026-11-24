package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.repository.AuditLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes an audit row in its own transaction so it survives the caller's rollback. */
@Component
@RequiredArgsConstructor
public class AuditRecorder {
    private final AuditLogRepository auditLogs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Employee actor, String action, String targetType, UUID targetId, String description) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDescription(description);
        auditLogs.save(log);
    }
}
