package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.util.EmailUtils;
import com.vertex.restosphere.dto.request.LoginRequest;
import com.vertex.restosphere.dto.response.EmployeeResponse;
import com.vertex.restosphere.dto.response.TokenResponse;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.mapper.EmployeeMapper;
import com.vertex.restosphere.repository.EmployeeRepository;
import com.vertex.restosphere.security.AccessTokenIssuer;
import com.vertex.restosphere.security.AppUserPrincipal;
import com.vertex.restosphere.security.FailureLimiter;
import com.vertex.restosphere.security.JwtProperties;
import com.vertex.restosphere.service.AuthService;
import com.vertex.restosphere.service.RefreshTokenService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final EmployeeRepository userRepository;
    private final EmployeeMapper userMapper;
    private final JwtProperties properties;

    private final FailureLimiter failures = new FailureLimiter(10, 15 * 60_000L);

    @Override
    public TokenResponse login(LoginRequest request) {
        String key = EmailUtils.normalize(request.email());
        if (failures.blocked(key)) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again later.");
        }
        Authentication authentication;
        try {
            authentication =
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(
                                    request.email(), request.password()));
        } catch (AuthenticationException e) {
            failures.fail(key);
            throw e;
        }
        failures.clear(key);
        Employee user = ((AppUserPrincipal) authentication.getPrincipal()).user();
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
    public EmployeeResponse currentUser(UUID userId) {
        return userRepository
                .findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private TokenResponse tokenResponse(Employee user, String refreshToken) {
        return TokenResponse.bearer(
                accessTokenIssuer.issue(user),
                refreshToken,
                properties.accessTokenTtl().toSeconds());
    }
}
