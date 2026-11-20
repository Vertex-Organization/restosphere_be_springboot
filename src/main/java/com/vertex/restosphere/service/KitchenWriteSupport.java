package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.User;

public interface KitchenWriteSupport {

    void commit(User user, String commandType);
}
