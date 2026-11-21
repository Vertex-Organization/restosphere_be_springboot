package com.vertex.restosphere.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank
                @Size(min = 32, message = "JWT_SECRET must be set and at least 32 characters long")
                String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {}
