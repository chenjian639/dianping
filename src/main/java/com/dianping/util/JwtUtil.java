package com.dianping.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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

    public JwtUtil(
            @Value("${jwt.secret}")
            String secret) {

        this.secretKey =
                Keys.hmacShaKeyFor(
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
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

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}