package com.vertex.restosphere.common.util;

import com.vertex.restosphere.common.enums.PriceUnit;
import com.vertex.restosphere.entity.ImmutableEntity;
import com.vertex.restosphere.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Đọc và kiểm tra giá trị từ payload dạng Map, và dựng Map phản hồi. */
public final class Values {

    private static final Map<String, String> FIELD_LABELS =
            Map.ofEntries(
                    Map.entry("reason", "Lý do"),
                    Map.entry("name", "Tên khách"),
                    Map.entry("phone", "Số điện thoại"),
                    Map.entry("guests", "Số khách"),
                    Map.entry("actualGuests", "Số khách thực tế"),
                    Map.entry("duration", "Thời lượng"),
                    Map.entry("durationMinutes", "Thời lượng"),
                    Map.entry("areaId", "Khu vực"),
                    Map.entry("tableId", "Bàn"),
                    Map.entry("date", "Ngày đặt"),
                    Map.entry("time", "Giờ đến"),
                    Map.entry("code", "Mã đặt bàn"),
                    Map.entry("quantity", "Số lượng"),
                    Map.entry("id", "Mã lịch đặt"));

    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999");

    private Values() {}

    public static ApiException invalid(String message) {
        return ApiException.badRequest(message);
    }

    public static ApiException invalid(String field, String message) {
        return ApiException.badRequest(field, message);
    }

    /** Dựng Map giữ thứ tự chèn, bỏ các cặp có giá trị null. */
    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                result.put(pairs[i].toString(), pairs[i + 1]);
            }
        }
        return result;
    }

    public static String id(ImmutableEntity entity) {
        return entity == null ? "" : entity.getId().toString();
    }

    /** Mã ngắn dễ đọc, ví dụ {@code B-1A2B3C4D}. */
    // UUIDv7 starts with a timestamp, so take the random tail to keep codes distinct.
    public static String code(String prefix, ImmutableEntity entity) {
        if (entity == null) return "";
        String id = entity.getId().toString();
        return prefix + "-" + id.substring(id.length() - 8).toUpperCase();
    }

    public static String str(Object value) {
        return Objects.toString(value, "");
    }

    public static String label(String key) {
        return FIELD_LABELS.getOrDefault(key, key);
    }

    public static String text(Map<String, Object> input, String key) {
        return Objects.toString(input.get(key), "").trim();
    }

    /** Số điện thoại đã bỏ khoảng trắng, dấu chấm, gạch và ngoặc. */
    public static String phone(Map<String, Object> input) {
        String phone = required(input, "phone", 30).replaceAll("[\\s().-]", "");
        return phone.startsWith("+84") ? "0" + phone.substring(3) : phone;
    }

    public static String required(Map<String, Object> input, String key, int max) {
        String value = text(input, key);
        if (value.isBlank()) {
            throw invalid(key, "Vui lòng nhập " + label(key).toLowerCase() + ".");
        }
        if (value.length() > max) {
            throw invalid(key, label(key) + " tối đa " + max + " ký tự.");
        }
        return value;
    }

    public static BigDecimal number(Map<String, Object> input, String key) {
        try {
            return new BigDecimal(input.get(key).toString());
        } catch (Exception e) {
            throw invalid(key, label(key) + " không hợp lệ.");
        }
    }

    public static int integer(Map<String, Object> input, String key, int min, int max) {
        try {
            int n = number(input, key).intValueExact();
            if (n < min || n > max) {
                throw invalid(key, label(key) + " phải từ " + min + " đến " + max + ".");
            }
            return n;
        } catch (ArithmeticException e) {
            throw invalid(key, label(key) + " phải là số nguyên.");
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> input, String key) {
        if (!(input.get(key) instanceof Map)) {
            throw invalid(key, "Thiếu thông tin " + label(key).toLowerCase() + ".");
        }
        return (Map<String, Object>) input.get(key);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> lines(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)
                || list.size() > 100
                || list.stream().anyMatch(x -> !(x instanceof Map))) {
            throw invalid("Danh sách món không hợp lệ.");
        }
        return (List<Map<String, Object>>) value;
    }

    /** Làm tròn về đồng nguyên. */
    public static BigDecimal whole(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    /** Số tiền đồng nguyên không âm, tối đa 999.999.999.999. */
    public static BigDecimal money(Map<String, Object> input, String key) {
        return money(number(input, key), key);
    }

    public static BigDecimal money(BigDecimal value, String key) {
        BigDecimal n;
        try {
            n = new BigDecimal(value.toString());
        } catch (Exception e) {
            throw invalid("Invalid " + key);
        }
        if (n.signum() < 0 || n.scale() > 0 || n.compareTo(MAX_MONEY) > 0) {
            throw invalid("Invalid whole-dong amount: " + key);
        }
        return n;
    }

    public static void validateQuantity(
            com.vertex.restosphere.entity.MenuItem item, BigDecimal quantity) {
        boolean byWeight = item.getPriceUnit() == PriceUnit.KG;
        if (quantity.signum() <= 0
                || quantity.compareTo(new BigDecimal("999")) > 0
                || quantity.stripTrailingZeros().scale() > 3
                || (!byWeight && quantity.stripTrailingZeros().scale() > 0)) {
            throw invalid("Số lượng món không hợp lệ.");
        }
    }
}
