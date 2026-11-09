package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.LoginRequest;
import com.vertex.restosphere.dto.response.TokenResponse;
import com.vertex.restosphere.dto.response.UserResponse;
import java.util.UUID;

public interface AuthService {

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(String refreshToken);

    void logout(String refreshToken);

    UserResponse currentUser(UUID userId);
}
