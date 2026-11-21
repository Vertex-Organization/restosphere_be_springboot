package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.User;

public interface RefreshTokenService {

    record RotatedToken(User user, String refreshToken) {}

    String issue(User user);

    RotatedToken rotate(String rawToken);

    void revoke(String rawToken);
}
