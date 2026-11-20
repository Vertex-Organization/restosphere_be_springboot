package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.RestaurantSettings;

public interface RestaurantSettingsProvider {

    RestaurantSettings lockAndGet();
}
