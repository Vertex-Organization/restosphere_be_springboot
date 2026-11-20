package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.User;

public interface AccountMailService {

    boolean isEnabled();

    void sendTemporaryPassword(User user, String temporaryPassword);
}
