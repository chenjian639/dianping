package com.dianping.util;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private static final String SECRET = "campus-local-only-secret-at-least-32-characters";

    @Test void concurrentSigningAndParsingAcrossInstances() throws Exception {
        JwtUtil issuer = new JwtUtil(SECRET);
        String token = issuer.generateToken(1L,"student","USER",null);
        JwtUtil verifier = new JwtUtil(SECRET);
        ExecutorService workers = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> jobs = new ArrayList<>();
        try {
            for (int i = 0; i < 32; i++) jobs.add(workers.submit(() -> {
                start.await();
                for (int request = 0; request < 100; request++) {
                    var claims = verifier.parseToken(token);
                    assertEquals("1",claims.getSubject());
                    assertEquals("USER",claims.get("role",String.class));
                    assertNull(claims.get("restaurantId",Number.class));
                    String merchant = issuer.generateToken(2L,"merchant","MERCHANT",123L);
                    assertEquals(123L, verifier.parseToken(merchant).get("restaurantId",Number.class).longValue());
                }
                return null;
            }));
            start.countDown();
            for (Future<?> job : jobs) job.get(30,TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
    }

    @Test void wrongKeysExpiredAndTamperedTokensAreRejected() {
        JwtUtil jwt = new JwtUtil(SECRET);
        String token = jwt.generateToken(1L,"student","USER",null);
        assertThrows(io.jsonwebtoken.JwtException.class, () -> new JwtUtil("another-secret-with-at-least-32-characters").parseToken(token));
        String[] parts = token.split("\\.");
        String signature = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        assertThrows(io.jsonwebtoken.JwtException.class, () -> jwt.parseToken(parts[0]+"."+parts[1]+"."+signature));
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expired = io.jsonwebtoken.Jwts.builder().subject("1")
                .expiration(new java.util.Date(System.currentTimeMillis()-60000)).signWith(key).compact();
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> jwt.parseToken(expired));
    }
}
