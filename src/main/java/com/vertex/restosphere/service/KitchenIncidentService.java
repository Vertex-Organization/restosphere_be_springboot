package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.KitchenIncidentRequest;
import com.vertex.restosphere.dto.response.KitchenIncidentResponse;
import com.vertex.restosphere.entity.Incident;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.IncidentRepository;
import com.vertex.restosphere.repository.UserRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Su co bep (REST). Giong ServiceWorkflow.kitchen "report-incident", bao MANAGER. */
@Service
@RequiredArgsConstructor
public class KitchenIncidentService {

    private static final Set<String> TYPES = Set.of("OUT_OF_STOCK", "EQUIPMENT", "CANNOT_PREPARE",
            "NOTE_CLARIFICATION", "OTHER");

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final WorkspaceStore store;
    private final ServiceWorkflow workflow;
    private final KitchenWriteSupport writes;
    private final IncidentRepository incidents;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<KitchenIncidentResponse> list(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.KITCHEN);
        List<UUID> managers = managerIds();
        return incidents.findAllByOrderByCreatedAtAsc().stream().map(i -> toResponse(i, managers)).toList();
    }

    @Transactional
    public KitchenIncidentResponse report(UUID actorId, KitchenIncidentRequest req) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.KITCHEN);
        restaurantLock.acquire();
        String kind = req.type().trim();
        if (!TYPES.contains(kind)) {
            throw bad("Invalid incident type");
        }
        String description = req.description().trim();
        if (description.isEmpty() || description.length() > 2000) {
            throw bad("description is required (max 2000)");
        }
        Incident.Severity severity;
        try {
            severity = Incident.Severity.valueOf(req.severity().trim());
        } catch (IllegalArgumentException e) {
            throw bad("Invalid severity");
        }
        Incident incident = new Incident();
        incident.setType(kind);
        incident.setTitle(kind);
        incident.setDescription(description);
        incident.setSeverity(severity);
        incident.setReporter(user);
        String itemId = trim(req.itemId());
        if (!itemId.isEmpty()) {
            OrderItem item = store.get(OrderItem.class, itemId);
            String orderId = trim(req.orderId());
            if (!orderId.isEmpty() && !item.getOrder().getId().toString().equals(orderId)) {
                throw bad("Incident item/order mismatch");
            }
            incident.setOrderItem(item);
        }
        incidents.save(incident);
        workflow.notify(Role.MANAGER, "INCIDENT", "Kitchen incident", incident.getDescription(),
                incident.getId().toString());
        writes.commit(user, "report-incident");
        store.flush();
        return toResponse(incident, managerIds());
    }

    // Giu hanh vi cu: thong bao cho moi tai khoan MANAGER.
    private List<UUID> managerIds() {
        return users.findAll().stream().filter(u -> u.getRole() == Role.MANAGER).map(User::getId).toList();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private KitchenIncidentResponse toResponse(Incident i, List<UUID> managers) {
        OrderItem item = i.getOrderItem();
        var response = i.getResolution() == null ? null : new KitchenIncidentResponse.Response(
                i.getAssignee() == null ? null : i.getAssignee().getId(), i.getUpdatedAt(), i.getResolution());
        return new KitchenIncidentResponse(i.getId(), "I-" + i.getId(), i.getType(), i.getSeverity(), i.getStatus(),
                i.getTitle(), i.getDescription(), item == null ? null : item.getOrder().getId(),
                item == null ? null : item.getId(), i.getReporter().getId(),
                i.getAssignee() == null ? null : i.getAssignee().getId(), i.getCreatedAt(), managers, response);
    }
}
