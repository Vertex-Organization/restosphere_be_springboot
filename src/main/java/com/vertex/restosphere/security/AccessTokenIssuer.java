package com.vertex.restosphere.security;

import com.vertex.restosphere.entity.Employee;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenIssuer {

    static final String ROLES_CLAIM = "roles";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public String issue(Employee user) {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .id(UUID.randomUUID().toString())
                        .issuer(properties.issuer())
                        .subject(user.getId().toString())
                        .issuedAt(now)
                        .expiresAt(now.plus(properties.accessTokenTtl()))
                        .claim("email", user.getEmail())
                        .claim(ROLES_CLAIM, List.of(user.getRole().name()))
                        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
