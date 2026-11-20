package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.RestaurantSettingsProvider;
import com.vertex.restosphere.service.WorkspaceStore;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
