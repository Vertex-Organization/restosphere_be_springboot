package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.RestaurantSettings;

/** Khoa roi lay dong restaurant_settings duy nhat. Chi goi trong transaction. */
public interface RestaurantSettingsProvider {

    RestaurantSettings lockAndGet();
}
