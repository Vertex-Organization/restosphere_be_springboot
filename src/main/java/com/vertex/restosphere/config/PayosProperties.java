package com.vertex.restosphere.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.payos")
public record PayosProperties(
        boolean fake,
        String clientId,
        String apiKey,
        String checksumKey,
        @NotBlank String returnUrl,
        @NotBlank String cancelUrl,
        @NotNull Duration billLinkTtl,
        @NotNull Duration timeout) {

    private static final String PLACEHOLDER = "not-configured";

    /** True only when all three keys were really provided (blank or the placeholder is not). */
    public boolean configured() {
        return real(clientId) && real(apiKey) && real(checksumKey);
    }

    private static boolean real(String key) {
        return key != null && !key.isBlank() && !PLACEHOLDER.equals(key);
    }
}
