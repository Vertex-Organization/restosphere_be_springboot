package com.vertex.restosphere.service;

public interface ImageStorageService {

    boolean isEnabled();

    String uploadRestaurantImage(String kind, byte[] bytes);
}
