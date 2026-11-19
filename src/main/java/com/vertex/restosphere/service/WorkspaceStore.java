package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceStore {
    @PersistenceContext EntityManager em;
    public <T> List<T> all(Class<T> type) { return em.createQuery("from " + type.getSimpleName(), type).getResultList(); }
    public <T> T get(Class<T> type, String id) {
        UUID uuid;
        try { uuid = UUID.fromString(id); } catch (Exception e) { throw invalid("Invalid " + type.getSimpleName() + " id"); }
        T value = em.find(type, uuid);
        if (value == null) throw ApiException.notFound(type.getSimpleName());
        return value;
    }
    public <T> T save(T value) { em.persist(value); return value; }
    public void remove(Object value) { em.remove(value); }
    public void flush() { em.flush(); }
    public RestaurantSettings lock() {
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
    public static String id(BaseEntity entity) { return entity == null ? "" : entity.getId().toString(); }
    /** Short readable display code: prefix + first 8 id chars, e.g. "R-F6933063" (instead of the full UUID). */
    public static String code(String prefix, BaseEntity entity) {
        return entity == null ? "" : prefix + "-" + entity.getId().toString().substring(0, 8).toUpperCase();
    }
}
