package com.example.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventory.config.AppProperties;
import com.example.inventory.user.Role;
import com.example.inventory.user.User;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure unit test — no Spring context, no Docker. */
class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtc2lnbmluZw==";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Jwt(SECRET, "inventory-api", Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Storage(
                        "bucket",
                        "us-east-1",
                        "",
                        true,
                        Duration.ofMinutes(15),
                        5_242_880L,
                        List.of(),
                        10_485_760L,
                        List.of()));

        jwtService = new JwtService(properties);
        user = new User("user@example.com", "hash", "Test User", Role.STAFF);
        setId(user, 42L);
    }

    @Test
    @DisplayName("an access token round-trips with subject, role and email")
    void accessTokenRoundTrips() {
        String token = jwtService.issueAccessToken(user);

        var claims = jwtService.parseAccessToken(token);
        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("42");
        assertThat(claims.get().get("role", String.class)).isEqualTo("STAFF");
        assertThat(claims.get().get("email", String.class)).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("a refresh token is not accepted as an access token")
    void refreshTokenIsNotAnAccessToken() {
        String refreshToken = jwtService.issueRefreshToken(user);

        assertThat(jwtService.parseAccessToken(refreshToken)).isEmpty();
        assertThat(jwtService.parseRefreshToken(refreshToken)).isPresent();
    }

    @Test
    @DisplayName("an access token is not accepted as a refresh token")
    void accessTokenIsNotARefreshToken() {
        String accessToken = jwtService.issueAccessToken(user);

        assertThat(jwtService.parseRefreshToken(accessToken)).isEmpty();
    }

    @Test
    @DisplayName("a tampered token fails signature verification")
    void tamperedTokenIsRejected() {
        String token = jwtService.issueAccessToken(user);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "bm90LWEtc2lnbmF0dXJl";

        assertThat(jwtService.parseAccessToken(tampered)).isEmpty();
    }

    @Test
    @DisplayName("garbage input is rejected rather than throwing")
    void garbageIsRejected() {
        assertThat(jwtService.parseAccessToken("not-a-jwt")).isEmpty();
        assertThat(jwtService.parseAccessToken("")).isEmpty();
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void wrongSecretIsRejected() {
        AppProperties other = new AppProperties(
                new AppProperties.Cors(List.of()),
                new AppProperties.Jwt(
                        "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1hbHNvLWxvbmctZW5vdWdoLWZvci1IUzI1Ng==",
                        "inventory-api",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7)),
                new AppProperties.Storage(
                        "b", "us-east-1", "", true, Duration.ofMinutes(1), 1L, List.of(), 1L, List.of()));

        String foreignToken = new JwtService(other).issueAccessToken(user);

        assertThat(jwtService.parseAccessToken(foreignToken)).isEmpty();
    }

    private static void setId(User user, Long id) throws Exception {
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}
