package com.vertex.restosphere.config;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.util.EmailUtils;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

/**
 * Tao tai khoan admin dau tien tu ADMIN_EMAIL/ADMIN_PASSWORD. Bo qua neu thieu bien moi truong hoac
 * email da ton tai, nen khong ghi de mat khau da doi.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class AdminBootstrapConfig {

    @Bean
    ApplicationRunner bootstrapAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.bootstrap-admin.email:}") String email,
            @Value("${app.security.bootstrap-admin.password:}") String password) {
        return args -> {
            if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
                return;
            }
            String normalizedEmail = EmailUtils.normalize(email);
            if (userRepository.existsByEmail(normalizedEmail)) {
                return;
            }
            User admin = new User();
            admin.setEmail(normalizedEmail);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setFullName("Administrator");
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
            log.info("Created bootstrap admin account {}", normalizedEmail);
        };
    }
}
