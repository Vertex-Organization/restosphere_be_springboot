package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.RestaurantSettings;
import java.math.BigDecimal;
import java.util.Objects;

/** Cung shape voi SystemSettingsRequest; field chu tra "" thay cho null. */
public record SystemSettingsResponse(boolean onlineReservations, int tableHoldMinutes, int noShowMinutes,
        int bufferMinutes, Billing billing, String timezone, String currency, boolean emailNotifications) {

    public record Billing(BigDecimal serviceChargePercent, BigDecimal vatPercent, String bankAccount,
            String bankName, String bankAccountName) {
    }

    public static SystemSettingsResponse from(RestaurantSettings s) {
        return new SystemSettingsResponse(s.isOnlineReservations(), s.getHoldMinutes(), s.getNoShowMinutes(),
                s.getBufferMinutes(),
                new Billing(s.getServiceChargeRate(), s.getVatRate(), str(s.getBankAccountNumber()),
                        str(s.getBankName()), str(s.getBankAccountName())),
                str(s.getTimezone()), str(s.getCurrency()), s.isEmailNotifications());
    }

    private static String str(String value) {
        return Objects.toString(value, "");
    }
}
