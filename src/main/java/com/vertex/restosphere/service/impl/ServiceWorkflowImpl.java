package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.text;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Notification;
import com.vertex.restosphere.entity.NotificationRead;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WorkspaceStore;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServiceWorkflowImpl implements ServiceWorkflow {

    private final WorkspaceStore db;

    @Override
    public void notify(Role role, String kind, String title, String body, String target) {
        var n = new Notification();
        n.setAudienceRole(role);
        n.setKind(kind);
        n.setTitle(title);
        n.setBody(body);
        n.setPath(target);
        db.save(n);
    }

    @Override
    public void audit(User actor, String action, UUID target, String entity, String description) {
        var a = new AuditLog();
        a.setActor(actor);
        a.setAction(action);
        a.setTargetId(target);
        a.setTargetType(entity);
        a.setDescription(description);
        db.save(a);
    }

    @Override
    public void read(Map<String, Object> c, User user) {
        Set<String> ids = new HashSet<>();
        if (c.get("ids") instanceof List<?> list) {
            list.forEach(x -> ids.add(x.toString()));
        }
        if (!text(c, "id").isEmpty()) {
            ids.add(text(c, "id"));
        }
        Set<UUID> alreadyRead = new HashSet<>();
        for (var read :
                db.query(NotificationRead.class, "from NotificationRead r where r.user=?1", user)) {
            alreadyRead.add(read.getNotification().getId());
        }
        var visible =
                db.query(
                        Notification.class,
                        "from Notification n where n.audienceRole=?1 or n.recipient=?2",
                        user.getRole(),
                        user);
        for (var n : visible) {
            boolean wanted = ids.isEmpty() || ids.contains(id(n));
            if (wanted && !alreadyRead.contains(n.getId())) {
                var read = new NotificationRead();
                read.setNotification(n);
                read.setUser(user);
                read.setReadAt(Instant.now());
                db.save(read);
            }
        }
    }
}
