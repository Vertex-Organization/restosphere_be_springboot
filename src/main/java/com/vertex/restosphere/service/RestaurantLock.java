package com.vertex.restosphere.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Khoa toan nha hang o muc DB (advisory lock cua PostgreSQL), tu nha khi transaction ket thuc.
 * Dung CUNG key voi WorkspaceStore.lock() de API REST va workspace command chay tuan tu voi nhau,
 * ke ca khi chay nhieu instance. Chi goi ben trong transaction.
 */
@Component
public class RestaurantLock {

    @PersistenceContext
    private EntityManager em;

    public void acquire() {
        // Key 738246110 phai trung voi WorkspaceStore.lock().
        em.createNativeQuery("select pg_advisory_xact_lock(738246110)", Object.class).getResultList();
    }
}
