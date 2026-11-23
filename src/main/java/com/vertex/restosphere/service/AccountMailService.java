package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.Employee;

public interface AccountMailService {

    boolean isEnabled();

    void sendTemporaryPassword(Employee user, String temporaryPassword);
}
