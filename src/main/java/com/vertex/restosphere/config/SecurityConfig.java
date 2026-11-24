package com.vertex.restosphere.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private static final RequestMatcher TOKEN_ENDPOINTS =
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, "/api/v1/auth/{action:login|refresh|logout}");

    // payOS posts here without a token; the handler verifies the signature itself.
    private static final String PAYOS_WEBHOOK = "/api/payments/payos-webhook";
    // Dev-only simulator; the controller itself only exists under the dev profile.
    private static final String DEV_PAYOS = "/api/dev/payos/**";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment)
            throws Exception {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        return http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(TOKEN_ENDPOINTS)
                                        .permitAll()
                                        .requestMatchers("/actuator/health/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/menu/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/reservations/settings")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/reservations",
                                                "/api/v1/reservations/lookup",
                                                "/api/v1/reservations/cancel",
                                                "/api/v1/reservations/payment-status",
                                                "/api/v1/customers/register")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, PAYOS_WEBHOOK)
                                        .permitAll()
                                        .requestMatchers(
                                                request ->
                                                        dev
                                                                ? PathPatternRequestMatcher
                                                                        .withDefaults()
                                                                        .matcher(DEV_PAYOS)
                                                                        .matches(request)
                                                                : false)
                                        .permitAll()
                                        .requestMatchers("/error")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.bearerTokenResolver(bearerTokenResolver())
                                        .jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Bo qua header Authorization o cac endpoint cap token: client thuong gui kem access token da
     * het han khi goi refresh, neu khong bo qua thi filter se tra 401 truoc khi vao controller.
     */
    private BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> TOKEN_ENDPOINTS.matches(request) ? null : delegate.resolve(request);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
