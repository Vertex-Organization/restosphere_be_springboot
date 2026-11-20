package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.RestaurantSettings;
import java.util.List;

/** Truy cap entity chung cho cac workflow. Helper tinh (map, id, invalid) o WorkspaceStoreImpl. */
public interface WorkspaceStore {

    <T> List<T> all(Class<T> type);

    <T> List<T> query(Class<T> type, String jpql, Object... params);

    <T> T get(Class<T> type, String id);

    <T> T save(T value);

    void remove(Object value);

    void flush();

    RestaurantSettings lock();
}
