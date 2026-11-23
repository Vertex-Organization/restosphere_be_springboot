package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.util.HashUtils;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.RefreshToken;
import com.vertex.restosphere.exception.InvalidRefreshTokenException;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.security.JwtProperties;
import com.vertex.restosphere.service.RefreshTokenService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String issue(Employee user) {
        refreshTokenRepository.deleteExpiredByUser(user.getId(), Instant.now());
        return create(user, UUID.randomUUID());
    }

    /**
     * Token da thu hoi ma van duoc gui lai nghia la token co the da bi lo, nen thu hoi ca family.
     * noRollbackFor giu lai cac thay doi thu hoi khi nem exception.
     */
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotatedToken rotate(String rawToken) {
        RefreshToken current =
                refreshTokenRepository
                        .findByTokenHashForUpdate(HashUtils.sha256Hex(rawToken))
                        .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();

        if (current.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }

        current.setRevokedAt(now);
        Employee user = current.getEmployee();
        if (current.getExpiresAt().isBefore(now) || !user.isEnabled()) {
            throw new InvalidRefreshTokenException();
        }

        return new RotatedToken(user, create(user, current.getFamilyId()));
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository
                .findByTokenHash(HashUtils.sha256Hex(rawToken))
                .ifPresent(
                        token ->
                                refreshTokenRepository.revokeFamily(
                                        token.getFamilyId(), Instant.now()));
    }

    private String create(Employee user, UUID familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setEmployee(user);
        token.setTokenHash(HashUtils.sha256Hex(rawToken));
        token.setFamilyId(familyId);
        token.setExpiresAt(Instant.now().plus(properties.refreshTokenTtl()));
        refreshTokenRepository.save(token);
        return rawToken;
    }
}
