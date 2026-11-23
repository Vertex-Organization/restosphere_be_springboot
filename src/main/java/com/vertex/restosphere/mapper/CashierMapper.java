package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.code;
import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;
import static com.vertex.restosphere.common.util.Values.str;

import com.vertex.restosphere.entity.CashierShift;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.ShiftAssignment;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Ca thu ngân, ca được xếp và cấu hình thanh toán dạng JSON. */
@Component
public class CashierMapper {

    public Map<String, Object> toMap(CashierShift x) {
        return map(
                "id",
                id(x),
                "code",
                code("S", x),
                "cashierId",
                id(x.getCashier()),
                "status",
                x.getClosedAt() == null ? "OPEN" : "CLOSED",
                "openedAt",
                x.getOpenedAt(),
                "openingFloat",
                x.getOpeningCash(),
                "closedAt",
                x.getClosedAt(),
                "expectedCash",
                x.getExpectedCash(),
                "countedCash",
                x.getCountedCash(),
                "closingNote",
                x.getNote());
    }

    public Map<String, Object> toScheduledMap(ShiftAssignment a) {
        return map(
                "id",
                id(a.getShift()),
                "name",
                a.getShift().getName(),
                "start",
                a.getShift().getStartsAt(),
                "end",
                a.getShift().getEndsAt(),
                "cashierId",
                id(a.getEmployee()));
    }

    public Map<String, Object> toConfigMap(RestaurantSettings s) {
        boolean hasBank = !str(s.getBankAccountNumber()).isEmpty();
        Map<String, Object> config =
                map(
                        "restaurantName",
                        s.getName(),
                        "address",
                        s.getAddress(),
                        "serviceChargeRate",
                        s.getServiceChargeRate().movePointRight(2),
                        "taxRate",
                        s.getVatRate().movePointRight(2),
                        "methods",
                        map("CASH", true, "TRANSFER", hasBank, "CARD", false),
                        "bank",
                        hasBank
                                ? map(
                                        "bankName",
                                        str(s.getBankName()),
                                        "accountNumber",
                                        s.getBankAccountNumber(),
                                        "accountName",
                                        str(s.getBankAccountName()))
                                : null);
        config.putIfAbsent("bank", null);
        return config;
    }
}
