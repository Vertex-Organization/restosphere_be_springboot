package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.AdminAuditAction;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.util.Base64ImageDecoder;
import com.vertex.restosphere.dto.request.RestaurantImageRequest;
import com.vertex.restosphere.dto.request.RestaurantProfileRequest;
import com.vertex.restosphere.dto.response.RestaurantProfileResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.AdminRestaurantService;
import com.vertex.restosphere.service.ImageStorageService;
import com.vertex.restosphere.service.WorkspaceStore;
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
public class AdminRestaurantServiceImpl implements AdminRestaurantService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private record Field(
            String name,
            Function<RestaurantProfileRequest, String> in,
            Function<RestaurantSettings, String> get,
            BiConsumer<RestaurantSettings, String> set,
            int max,
            boolean link) {}

    private static final List<Field> FIELDS =
            List.of(
                    new Field(
                            "name",
                            RestaurantProfileRequest::name,
                            RestaurantSettings::getName,
                            RestaurantSettings::setName,
                            150,
                            false),
                    new Field(
                            "description",
                            RestaurantProfileRequest::description,
                            RestaurantSettings::getDescription,
                            RestaurantSettings::setDescription,
                            255,
                            false),
                    new Field(
                            "address",
                            RestaurantProfileRequest::address,
                            RestaurantSettings::getAddress,
                            RestaurantSettings::setAddress,
                            255,
                            false),
                    new Field(
                            "hotline",
                            RestaurantProfileRequest::hotline,
                            RestaurantSettings::getPhone,
                            RestaurantSettings::setPhone,
                            30,
                            false),
                    new Field(
                            "email",
                            RestaurantProfileRequest::email,
                            RestaurantSettings::getEmail,
                            RestaurantSettings::setEmail,
                            255,
                            false),
                    new Field(
                            "openingHours",
                            RestaurantProfileRequest::openingHours,
                            RestaurantSettings::getOpeningHours,
                            RestaurantSettings::setOpeningHours,
                            255,
                            false),
                    new Field(
                            "facebook",
                            RestaurantProfileRequest::facebook,
                            RestaurantSettings::getFacebook,
                            RestaurantSettings::setFacebook,
                            255,
                            true),
                    new Field(
                            "instagram",
                            RestaurantProfileRequest::instagram,
                            RestaurantSettings::getInstagram,
                            RestaurantSettings::setInstagram,
                            255,
                            true),
                    new Field(
                            "tiktok",
                            RestaurantProfileRequest::tiktok,
                            RestaurantSettings::getTiktok,
                            RestaurantSettings::setTiktok,
                            255,
                            true));

    private final WorkspaceStore store;
    private final CurrentUserService currentUser;
    private final AuditLogRepository auditLogs;
    private final ImageStorageService imageStorage;

    @Override
    public RestaurantProfileResponse get(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN);
        return RestaurantProfileResponse.from(store.lock());
    }

    @Override
    public RestaurantProfileResponse update(UUID actorId, RestaurantProfileRequest req) {
        RestaurantSettings settings = store.lock();
        User actor = currentUser.require(actorId, Role.ADMIN);

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

        audit(actor, settings, String.join(", ", changed));
        return RestaurantProfileResponse.from(settings);
    }

    @Override
    public RestaurantProfileResponse uploadImage(
            UUID actorId, String kind, RestaurantImageRequest req) {
        String field = imageField(kind);
        RestaurantSettings settings = store.lock();
        User actor = currentUser.require(actorId, Role.ADMIN);
        if (!imageStorage.isEnabled()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Image upload is not configured");
        }
        byte[] bytes = Base64ImageDecoder.decode(req.contentType(), req.data());
        String url = imageStorage.uploadRestaurantImage(kind, bytes);
        setImage(settings, kind, url);
        audit(actor, settings, field);
        return RestaurantProfileResponse.from(settings);
    }

    @Override
    public RestaurantProfileResponse removeImage(UUID actorId, String kind) {
        String field = imageField(kind);
        RestaurantSettings settings = store.lock();
        User actor = currentUser.require(actorId, Role.ADMIN);
        String current = kind.equals("logo") ? settings.getLogoUrl() : settings.getCoverUrl();
        if (current == null) {
            return RestaurantProfileResponse.from(settings);
        }
        setImage(settings, kind, null);
        audit(actor, settings, field);
        return RestaurantProfileResponse.from(settings);
    }

    private static String imageField(String kind) {
        return switch (kind) {
            case "logo" -> "logoUrl";
            case "cover" -> "coverUrl";
            default -> throw invalid("Image kind must be logo or cover");
        };
    }

    private static void setImage(RestaurantSettings settings, String kind, String url) {
        if (kind.equals("logo")) {
            settings.setLogoUrl(url);
        } else {
            settings.setCoverUrl(url);
        }
    }

    private void audit(User actor, RestaurantSettings settings, String fields) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(AdminAuditAction.RESTAURANT_UPDATED.name());
        log.setTargetType("SETTINGS");
        log.setTargetId(settings.getId());
        log.setDescription("Restaurant profile updated: " + fields);
        auditLogs.save(log);
    }

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
