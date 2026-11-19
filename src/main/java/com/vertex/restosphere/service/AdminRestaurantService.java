package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.AdminAuditAction;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.RestaurantProfileRequest;
import com.vertex.restosphere.dto.response.RestaurantProfileResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminRestaurantService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Mot field cua ho so: ten trong API, cach doc request, doc/ghi entity, do dai toi da, co phai link khong. */
    private record Field(String name, Function<RestaurantProfileRequest, String> in,
            Function<RestaurantSettings, String> get, BiConsumer<RestaurantSettings, String> set,
            int max, boolean link) {
    }

    // Thu tu nay quyet dinh thu tu ten field trong audit log.
    private static final List<Field> FIELDS = List.of(
            new Field("name", RestaurantProfileRequest::name, RestaurantSettings::getName, RestaurantSettings::setName, 150, false),
            new Field("description", RestaurantProfileRequest::description, RestaurantSettings::getDescription, RestaurantSettings::setDescription, 255, false),
            new Field("address", RestaurantProfileRequest::address, RestaurantSettings::getAddress, RestaurantSettings::setAddress, 255, false),
            new Field("hotline", RestaurantProfileRequest::hotline, RestaurantSettings::getPhone, RestaurantSettings::setPhone, 30, false),
            new Field("email", RestaurantProfileRequest::email, RestaurantSettings::getEmail, RestaurantSettings::setEmail, 255, false),
            new Field("openingHours", RestaurantProfileRequest::openingHours, RestaurantSettings::getOpeningHours, RestaurantSettings::setOpeningHours, 255, false),
            new Field("facebook", RestaurantProfileRequest::facebook, RestaurantSettings::getFacebook, RestaurantSettings::setFacebook, 255, true),
            new Field("instagram", RestaurantProfileRequest::instagram, RestaurantSettings::getInstagram, RestaurantSettings::setInstagram, 255, true),
            new Field("tiktok", RestaurantProfileRequest::tiktok, RestaurantSettings::getTiktok, RestaurantSettings::setTiktok, 255, true),
            new Field("logoUrl", RestaurantProfileRequest::logoUrl, RestaurantSettings::getLogoUrl, RestaurantSettings::setLogoUrl, 255, true),
            new Field("coverUrl", RestaurantProfileRequest::coverUrl, RestaurantSettings::getCoverUrl, RestaurantSettings::setCoverUrl, 255, true));

    private final RestaurantSettingsProvider provider;
    private final CurrentUserService currentUser;
    private final AuditLogRepository auditLogs;

    public RestaurantProfileResponse get(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN);
        return RestaurantProfileResponse.from(provider.lockAndGet());
    }

    public RestaurantProfileResponse update(UUID actorId, RestaurantProfileRequest req) {
        // Khoa truoc roi moi kiem tra actor, giong A1/A2.
        RestaurantSettings settings = provider.lockAndGet();
        User actor = currentUser.require(actorId, Role.ADMIN);

        // Kiem tra het truoc khi gan de request sai khong de lai thay doi nua chung.
        List<String> values = FIELDS.stream().map(f -> validate(f, req)).toList();
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < FIELDS.size(); i++) {
            Field f = FIELDS.get(i);
            if (!Objects.equals(f.get().apply(settings), values.get(i))) {
                f.set().accept(settings, values.get(i));
                changed.add(f.name());
            }
        }
        if (changed.isEmpty()) {
            return RestaurantProfileResponse.from(settings);
        }

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(AdminAuditAction.RESTAURANT_UPDATED.name());
        log.setTargetType("SETTINGS");
        log.setTargetId(settings.getId());
        log.setDescription("Restaurant profile updated: " + String.join(", ", changed));
        auditLogs.save(log);
        return RestaurantProfileResponse.from(settings);
    }

    /** Cat khoang trang, chuoi rong -> null, roi kiem tra theo quy tac cua field. */
    private static String validate(Field f, RestaurantProfileRequest req) {
        String raw = f.in().apply(req);
        String value = raw == null || raw.isBlank() ? null : raw.strip();
        if (f.name().equals("name") && (value == null || value.length() < 2)) {
            throw invalid("name must be between 2 and 150 characters");
        }
        if (value == null) {
            return null;
        }
        if (value.length() > f.max()) {
            throw invalid(f.name() + " must be at most " + f.max() + " characters");
        }
        if (f.name().equals("email") && !EMAIL.matcher(value).matches()) {
            throw invalid("email is invalid");
        }
        if (f.link() && !value.startsWith("https://")) {
            throw invalid(f.name() + " must start with https://");
        }
        return value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
