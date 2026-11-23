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
import com.vertex.restosphere.security.JwtProperties;
import com.vertex.restosphere.service.AuthService;
import com.vertex.restosphere.service.RefreshTokenService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final int MAX_FAILURES = 10;
    private static final long WINDOW_MS = 15 * 60_000L;
    // shortcut: per-email, in-memory (single instance), lets an attacker lock a known email for 15
    // min; move to shared store + per-IP when scaling out.
    private final Map<String, long[]> failures = new ConcurrentHashMap<>();

    @Override
    public TokenResponse login(LoginRequest request) {
        String key = EmailUtils.normalize(request.email());
        long now = System.currentTimeMillis();
        if (failures.size() > 10_000) {
            failures.values().removeIf(v -> now - v[1] >= WINDOW_MS);
        }
        long[] f = failures.get(key);
        if (f != null && now - f[1] < WINDOW_MS && f[0] >= MAX_FAILURES) {
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
            failures.compute(
                    key,
                    (k, v) ->
                            v == null || now - v[1] >= WINDOW_MS
                                    ? new long[] {1, now}
                                    : new long[] {v[0] + 1, v[1]});
            throw e;
        }
        failures.remove(key);
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
