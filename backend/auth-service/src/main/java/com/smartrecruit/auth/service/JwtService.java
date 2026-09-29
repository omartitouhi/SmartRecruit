package com.smartrecruit.auth.service;

import com.smartrecruit.auth.config.AuthProperties;
import com.smartrecruit.auth.model.Account;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and parses access JWTs. Claims follow {@code DATA_MODEL.md} §1: {@code sub} = account id,
 * {@code role}, {@code exp}.
 */
@Service
public class JwtService {

    public static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(AuthProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.jwt().accessTokenTtl();
        this.clock = clock;
    }

    public String generateAccessToken(Account account) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(account.getId().toString())
                .claim(ROLE_CLAIM, account.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.JwtException if the token is malformed, badly signed or expired
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }
}
