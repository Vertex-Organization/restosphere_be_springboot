package com.vertex.restosphere.security;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.UserRepository;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Doc lai user tu DB o moi request: access token song 15 phut, nen tai khoan vua bi khoa
 * hoac doi vai tro phai mat quyen ngay chu khong doi token het han.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository users;

    public User require(UUID userId, Role... allowed) {
        return users.findById(userId)
                .filter(u -> u.isEnabled() && Arrays.asList(allowed).contains(u.getRole()))
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Access denied"));
    }

    public static UUID idOf(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
