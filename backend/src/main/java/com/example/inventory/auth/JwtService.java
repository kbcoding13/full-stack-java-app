package com.example.inventory.auth;

import com.example.inventory.config.AppProperties;
import com.example.inventory.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and validates the access/refresh token pair. */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final AppProperties.Jwt properties;
    private final SecretKey signingKey;

    public JwtService(AppProperties properties) {
        this.properties = properties.jwt();
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(this.properties.secret()));
    }

    public String issueAccessToken(User user) {
        return issue(user, TYPE_ACCESS, properties.accessTokenTtl().toSeconds());
    }

    public String issueRefreshToken(User user) {
        return issue(user, TYPE_REFRESH, properties.refreshTokenTtl().toSeconds());
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    /** Parses an access token, returning empty if it is invalid, expired or the wrong type. */
    public Optional<Claims> parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    /** Parses a refresh token. Refresh tokens must never be accepted as access tokens. */
    public Optional<Claims> parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    private String issue(User user, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    private Optional<Claims> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
