package com.dianping.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    private static final String SECRET =
            "dianping-demo-secret-key-12345678901234567890";

    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000L;

    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public static String generateToken(
            Long userId,
            String username,
            String role,
            Long restaurantId) {

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("restaurantId", restaurantId)
                .issuedAt(new Date())
                .expiration(new Date(
                        System.currentTimeMillis() + EXPIRE_TIME
                ))
                .signWith(KEY)
                .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}