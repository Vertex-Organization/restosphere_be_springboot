package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.Employee;

public interface RefreshTokenService {

    record RotatedToken(Employee user, String refreshToken) {}

    String issue(Employee user);

    RotatedToken rotate(String rawToken);

    void revoke(String rawToken);
}
