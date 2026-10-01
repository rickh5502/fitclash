// File: src/main/java/com/fitclash/security/JwtService.java
package com.fitclash.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 token mint / verify. Fails fast at startup on a short secret rather than
 * at the first login, which is the cheapest place to catch a bad deployment.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlSeconds;
    private final String issuer;

    public JwtService(@Value("${fitclash.jwt.secret}") String secret,
                      @Value("${fitclash.jwt.expiration-seconds:86400}") long ttlSeconds,
                      @Value("${fitclash.jwt.issuer:fitclash-api}") String issuer) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(
                    "fitclash.jwt.secret must be at least 32 bytes for HS256 (got " + raw.length + ").");
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.ttlSeconds = ttlSeconds;
        this.issuer = issuer;
    }

    public String issue(UUID userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /** @return the verified principal, or null when the token is absent/expired/forged. */
    public AuthPrincipal verify(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            return new AuthPrincipal(UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
