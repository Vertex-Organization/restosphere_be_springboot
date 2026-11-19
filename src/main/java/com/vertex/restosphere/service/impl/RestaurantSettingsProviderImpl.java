package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.RestaurantSettingsProvider;
import com.vertex.restosphere.service.WorkspaceStore;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lay dong restaurant_settings duy nhat, tao neu chua co (giong WorkspaceStore.lock()). Chi goi trong transaction.
 * Phai khoa TRUOC khi doc: Hibernate ghi lai TOAN BO cot khi luu entity, neu mot workspace command
 * (bep, thu ngan...) doc settings truoc roi luu sau, no se ghi de thong tin vua sua bang gia tri cu.
 * Khoa chung key voi WorkspaceStore buoc hai ben chay lan luot, ben sau luon doc ban moi nhat.
 */
@Component
@RequiredArgsConstructor
public class RestaurantSettingsProviderImpl implements RestaurantSettingsProvider {

    private final RestaurantLock restaurantLock;
    private final RestaurantSettingsRepository settings;

    @Override
    public RestaurantSettings lockAndGet() {
        restaurantLock.acquire();
        return settings.findBySingletonKey(1).orElseGet(() -> {
            RestaurantSettings s = new RestaurantSettings();
            s.setName("RestoSphere");
            return settings.save(s);
        });
    }
}
