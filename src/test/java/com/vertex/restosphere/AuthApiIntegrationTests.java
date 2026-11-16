package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTests {

    private static final String EMAIL = "staff@restosphere.test";
    private static final String DISABLED_EMAIL = "disabled@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String ISSUER = "restosphere";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;

    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        user = saveUser(EMAIL, true);
        saveUser(DISABLED_EMAIL, false);
    }

    @Test
    void loginIssuesSignedJwtWithExpectedClaims() throws Exception {
        String body = login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("WAITER");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaims()).doesNotContainKey("password");
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        login("STAFF@RestoSphere.test", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        for (String[] credentials : new String[][] {
                {EMAIL, "wrong-password"},
                {"nobody@restosphere.test", PASSWORD},
                {DISABLED_EMAIL, PASSWORD}}) {
            login(credentials[0], credentials[1])
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }
    }

    @Test
    void loginValidatesPayload() throws Exception {
        login("not-an-email", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void protectedEndpointRequiresValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        String accessToken = JsonPath.read(loginBody(), "$.accessToken");
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("WAITER"));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAndForeignIssuerTokensAreRejected() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String expired = encode(ISSUER, past, past.plusSeconds(60));
        String foreignIssuer = encode("someone-else", Instant.now(), Instant.now().plusSeconds(600));

        for (String token : new String[] {expired, foreignIssuer}) {
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void refreshRotatesTokenAndWorksWithStaleBearerHeader() throws Exception {
        String oldRefresh = JsonPath.read(loginBody(), "$.refreshToken");

        String body = postToken("/api/v1/auth/refresh", oldRefresh, "Bearer expired.or.garbage")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newRefresh = JsonPath.read(body, "$.refreshToken");
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        postToken("/api/v1/auth/refresh", newRefresh, null).andExpect(status().isOk());
    }

    @Test
    void reusingRotatedTokenRevokesWholeFamily() throws Exception {
        String stolen = JsonPath.read(loginBody(), "$.refreshToken");
        String body = postToken("/api/v1/auth/refresh", stolen, null)
                .andReturn().getResponse().getContentAsString();
        String latest = JsonPath.read(body, "$.refreshToken");

        postToken("/api/v1/auth/refresh", stolen, null).andExpect(status().isUnauthorized());
        postToken("/api/v1/auth/refresh", latest, null).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRejectedOnceUserIsDisabled() throws Exception {
        String refresh = JsonPath.read(loginBody(), "$.refreshToken");
        user.setEnabled(false);
        userRepository.save(user);

        postToken("/api/v1/auth/refresh", refresh, null).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenAndIsIdempotent() throws Exception {
        String refresh = JsonPath.read(loginBody(), "$.refreshToken");

        postToken("/api/v1/auth/logout", refresh, null).andExpect(status().isNoContent());
        postToken("/api/v1/auth/logout", refresh, null).andExpect(status().isNoContent());
        postToken("/api/v1/auth/refresh", refresh, null).andExpect(status().isUnauthorized());
    }

    @Test
    void rawRefreshTokenIsNotStored() throws Exception {
        String refresh = JsonPath.read(loginBody(), "$.refreshToken");

        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .satisfies(token -> assertThat(token.getTokenHash()).isNotEqualTo(refresh).hasSize(64));
    }

    private User saveUser(String email, boolean enabled) {
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(PASSWORD));
        newUser.setFullName("Test User");
        newUser.setRole(Role.WAITER);
        newUser.setEnabled(enabled);
        return userRepository.save(newUser);
    }

    private String loginBody() throws Exception {
        return login(EMAIL, PASSWORD).andReturn().getResponse().getContentAsString();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions postToken(String path, String refreshToken, String authorization) throws Exception {
        var request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private String encode(String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
