package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.WorkspaceStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceStoreImpl implements WorkspaceStore {

    private static final String NOT_FOUND =
            "Không tìm thấy dữ liệu. Có thể đã bị xóa hoặc thay đổi, vui lòng tải lại.";

    @PersistenceContext private EntityManager em;
    private final RestaurantLock restaurantLock;

    @Override
    public <T> List<T> all(Class<T> type) {
        return em.createQuery("from " + type.getSimpleName(), type).getResultList();
    }

    @Override
    public <T> List<T> query(Class<T> type, String jpql, Object... params) {
        var q = em.createQuery(jpql, type);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return q.getResultList();
    }

    @Override
    public <T> T get(Class<T> type, String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Không tìm thấy dữ liệu. Vui lòng tải lại.");
        }
        T value = em.find(type, uuid);
        if (value == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return value;
    }

    @Override
    public <T> T save(T value) {
        em.persist(value);
        return value;
    }

    @Override
    public void remove(Object value) {
        em.remove(value);
    }

    @Override
    public void flush() {
        em.flush();
    }

    /** Lấy khóa toàn nhà hàng rồi trả cài đặt; tạo cài đặt mặc định ở lần đầu. */
    @Override
    public RestaurantSettings lock() {
        restaurantLock.acquire();
        var settings = all(RestaurantSettings.class);
        if (!settings.isEmpty()) {
            return settings.getFirst();
        }
        var value = new RestaurantSettings();
        value.setName("RestoSphere");
        return save(value);
    }
}
