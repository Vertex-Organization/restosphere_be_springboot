package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.LoginRequest;
import com.vertex.restosphere.dto.response.EmployeeResponse;
import com.vertex.restosphere.dto.response.TokenResponse;
import java.util.UUID;

public interface AuthService {

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(String refreshToken);

    void logout(String refreshToken);

    EmployeeResponse currentUser(UUID userId);
}
