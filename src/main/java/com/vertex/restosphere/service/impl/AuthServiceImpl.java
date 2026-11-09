package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.dto.request.LoginRequest;
import com.vertex.restosphere.dto.response.TokenResponse;
import com.vertex.restosphere.dto.response.UserResponse;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.mapper.UserMapper;
import com.vertex.restosphere.repository.UserRepository;
import com.vertex.restosphere.security.AccessTokenIssuer;
import com.vertex.restosphere.security.AppUserPrincipal;
import com.vertex.restosphere.security.JwtProperties;
import com.vertex.restosphere.service.AuthService;
import com.vertex.restosphere.service.RefreshTokenService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtProperties properties;

    @Override
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        User user = ((AppUserPrincipal) authentication.getPrincipal()).user();
        return tokenResponse(user, refreshTokenService.issue(user));
    }

    @Override
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(refreshToken);
        return tokenResponse(rotated.user(), rotated.refreshToken());
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Override
    public UserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private TokenResponse tokenResponse(User user, String refreshToken) {
        return TokenResponse.bearer(
                accessTokenIssuer.issue(user),
                refreshToken,
                properties.accessTokenTtl().toSeconds());
    }
}
