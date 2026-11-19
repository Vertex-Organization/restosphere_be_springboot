package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.RestaurantSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantSettingsRepository extends JpaRepository<RestaurantSettings, UUID> {

    Optional<RestaurantSettings> findBySingletonKey(int key);
}
