package com.dianping.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final long EXPIRE_TIME =
            24 * 60 * 60 * 1000L;

    private final SecretKey secretKey;
    private final JwtParser parser;

    public JwtUtil(
            @Value("${jwt.secret}")
            String secret) {

        this.secretKey =
                Keys.hmacShaKeyFor(
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
        // JwtParser is immutable/thread-safe; resolve JSON providers once at startup.
        this.parser = Jwts.parser().verifyWith(secretKey).build();
    }

    public String generateToken(
            Long userId,
            String username,
            String role,
            Long restaurantId) {

        return Jwts.builder()
                .subject(
                        String.valueOf(userId)
                )
                .claim(
                        "username",
                        username
                )
                .claim(
                        "role",
                        role
                )
                .claim(
                        "restaurantId",
                        restaurantId
                )
                .issuedAt(
                        new Date()
                )
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + EXPIRE_TIME
                        )
                )
                .signWith(secretKey)
                .compact();
    }

    public Claims parseToken(
            String token) {

        return parser.parseSignedClaims(token)
                .getPayload();
    }
}
