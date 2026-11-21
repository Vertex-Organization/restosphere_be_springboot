package com.vertex.restosphere.config;

import com.cloudinary.Cloudinary;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration
@ConditionalOnProperty(name = "app.cloudinary.enabled", havingValue = "true")
public class CloudinaryConfig {

    @Bean
    Cloudinary cloudinary(
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret) {
        Assert.hasText(cloudName, "CLOUDINARY_CLOUD_NAME is required when Cloudinary is enabled");
        Assert.hasText(apiKey, "CLOUDINARY_API_KEY is required when Cloudinary is enabled");
        Assert.hasText(apiSecret, "CLOUDINARY_API_SECRET is required when Cloudinary is enabled");
        return new Cloudinary(
                Map.of(
                        "cloud_name", cloudName,
                        "api_key", apiKey,
                        "api_secret", apiSecret,
                        "secure", true));
    }
}
