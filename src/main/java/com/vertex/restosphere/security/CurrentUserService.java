package com.vertex.restosphere.security;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.EmployeeRepository;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final EmployeeRepository users;

    public Employee require(UUID userId, Role... allowed) {
        return users.findById(userId)
                .filter(u -> u.isEnabled() && Arrays.asList(allowed).contains(u.getRole()))
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Access denied"));
    }

    public static UUID idOf(Jwt jwt) {
        String subject = jwt.getSubject();
        try {
            return UUID.fromString(subject == null ? "" : subject);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
    }
}
