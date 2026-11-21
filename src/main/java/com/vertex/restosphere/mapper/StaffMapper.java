package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;

import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.ShiftAssignment;
import com.vertex.restosphere.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Nhân viên dạng JSON, kèm quyền theo workspace và ca đang làm. */
@Component
public class StaffMapper {

    private static final List<String> CASHIER_PERMISSIONS =
            List.of(
                    "bills.view",
                    "bills.prepare",
                    "payments.create",
                    "payments.view",
                    "payments.confirm_transfer",
                    "receipts.print",
                    "transactions.view",
                    "discounts.apply",
                    "discounts.request",
                    "requests.create",
                    "shifts.view",
                    "shifts.manage");
    private static final List<String> KITCHEN_PERMISSIONS =
            List.of(
                    "kitchen.items.update",
                    "kitchen.incidents.create",
                    "kitchen.menu.availability");
    private static final List<String> DEFAULT_PERMISSIONS =
            List.of(
                    "tables.edit",
                    "reservations.edit",
                    "employees.assign",
                    "shifts.manage",
                    "menu.manage",
                    "approvals.decide",
                    "incidents.manage",
                    "reports.export");

    public Map<String, Object> toMap(
            User u,
            String workspace,
            RestaurantSettings settings,
            List<ShiftAssignment> assignments,
            Instant now) {
        return map(
                "id",
                id(u),
                "name",
                u.getFullName(),
                "fullName",
                u.getFullName(),
                "username",
                u.getEmail(),
                "role",
                u.getRole(),
                "position",
                u.getRole().name(),
                "shift",
                currentShift(assignments, now),
                "permissions",
                permissions(workspace),
                "discountLimitPercent",
                settings.getCashierDiscountPercent(),
                "approvalLimit",
                settings.getManagerApprovalLimit(),
                "department",
                department(u),
                "status",
                u.isEnabled() ? "ACTIVE" : "INACTIVE",
                "areaId",
                id(u.getAssignedArea()));
    }

    private static String currentShift(List<ShiftAssignment> assignments, Instant now) {
        return assignments.stream()
                .filter(
                        a ->
                                a.getShift().getStartsAt().isBefore(now)
                                        && a.getShift().getEndsAt().isAfter(now))
                .map(a -> a.getShift().getName())
                .findFirst()
                .orElse("");
    }

    private static List<String> permissions(String workspace) {
        return switch (workspace) {
            case "cashier" -> CASHIER_PERMISSIONS;
            case "kitchen" -> KITCHEN_PERMISSIONS;
            default -> DEFAULT_PERMISSIONS;
        };
    }

    private static String department(User u) {
        return switch (u.getRole()) {
            case WAITER -> "SERVICE";
            case KITCHEN -> "KITCHEN";
            case CASHIER -> "CASHIER";
            case RECEPTIONIST -> "RECEPTION";
            default -> "MANAGEMENT";
        };
    }
}
