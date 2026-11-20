package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.service.WorkspaceStore;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceStoreImpl implements WorkspaceStore {
    @PersistenceContext EntityManager em;
    @Override public <T> List<T> all(Class<T> type) { return em.createQuery("from " + type.getSimpleName(), type).getResultList(); }
    @Override public <T> List<T> query(Class<T> type, String jpql, Object... params) {
        var q = em.createQuery(jpql, type);
        for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
        return q.getResultList();
    }
    @Override public <T> T get(Class<T> type, String id) {
        UUID uuid;
        try { uuid = UUID.fromString(id); } catch (Exception e) { throw invalid("Không tìm thấy dữ liệu. Vui lòng tải lại."); }
        T value = em.find(type, uuid);
        if (value == null) throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu. Có thể đã bị xóa hoặc thay đổi, vui lòng tải lại.");
        return value;
    }
    @Override public <T> T save(T value) { em.persist(value); return value; }
    @Override public void remove(Object value) { em.remove(value); }
    @Override public void flush() { em.flush(); }
    @Override public RestaurantSettings lock() {
        // Serialize cross-domain commands across application instances, including first settings creation.
        em.createNativeQuery("select pg_advisory_xact_lock(738246110)", Object.class).getResultList();
        var settings = all(RestaurantSettings.class);
        if (!settings.isEmpty()) return settings.getFirst();
        var value = new RestaurantSettings(); value.setName("RestoSphere"); return save(value);
    }
    public static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    public static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) if(pairs[i+1]!=null) result.put(pairs[i].toString(), pairs[i+1]);
        return result;
    }
    public static String code(String prefix, BaseEntity entity) {
        return entity == null ? "" : prefix + "-" + entity.getId().toString().substring(0, 8).toUpperCase();
    }
    public static String id(BaseEntity entity) { return entity == null ? "" : entity.getId().toString(); }
}
