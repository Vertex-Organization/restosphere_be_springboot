package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.WorkspaceStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class RestaurantLockImpl implements RestaurantLock {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void acquire() {
        em.createNativeQuery("select pg_advisory_xact_lock(738246110)", Object.class).getResultList();
    }
}
