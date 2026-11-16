package com.vertex.restosphere;

import com.cloudinary.Cloudinary;
import com.vertex.restosphere.config.CloudinaryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinaryConfigTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CloudinaryConfig.class);

    @Test
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Cloudinary.class);
        });
    }

    @Test
    void enabledClientUsesConfiguredCredentialsAndHttps() {
        runner.withPropertyValues(
                "app.cloudinary.enabled=true",
                "app.cloudinary.cloud-name=test-cloud",
                "app.cloudinary.api-key=test-key",
                "app.cloudinary.api-secret=test-secret")
                .run(context -> {
                    assertThat(context).hasSingleBean(Cloudinary.class);
                    Cloudinary client = context.getBean(Cloudinary.class);
                    assertThat(client.config.cloudName).isEqualTo("test-cloud");
                    assertThat(client.config.apiKey).isEqualTo("test-key");
                    assertThat(client.config.apiSecret).isEqualTo("test-secret");
                    assertThat(client.url().generate("sample.jpg")).startsWith("https://");
                });
    }

    @Test
    void enabledClientRejectsMissingSecret() {
        runner.withPropertyValues(
                "app.cloudinary.enabled=true",
                "app.cloudinary.cloud-name=test-cloud",
                "app.cloudinary.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("CLOUDINARY_API_SECRET is required when Cloudinary is enabled");
                });
    }
}
