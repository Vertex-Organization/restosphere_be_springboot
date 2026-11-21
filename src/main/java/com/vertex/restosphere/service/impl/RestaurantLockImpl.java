package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.service.RestaurantLock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class RestaurantLockImpl implements RestaurantLock {

    /** Khóa advisory của Postgres dùng chung cho mọi thao tác ghi cần tuần tự hóa. */
    private static final long LOCK_KEY = 738246110L;

    @PersistenceContext private EntityManager em;

    @Override
    public void acquire() {
        em.createNativeQuery("select pg_advisory_xact_lock(" + LOCK_KEY + ")", Object.class)
                .getResultList();
    }
}
