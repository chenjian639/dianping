package com.dianping.security;

import com.dianping.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Used only in Spring Security's chain, not registered as a second servlet filter. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtUtil jwt;
    private final ApiErrorWriter errors;

    public JwtAuthenticationFilter(JwtUtil jwt, ApiErrorWriter errors) {
        this.jwt = jwt;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            try {
                if (!authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
                var claims = jwt.parseToken(authorization.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);
                if (role == null || !Set.of("USER", "MERCHANT", "ADMIN").contains(role))
                    throw new IllegalArgumentException();
                Number restaurant = claims.get("restaurantId", Number.class);
                request.setAttribute("userId", userId);
                request.setAttribute("role", role);
                request.setAttribute("restaurantId", restaurant == null ? null : restaurant.longValue());
                var auth = new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                errors.write(response, 401, "token无效或已过期");
                return;
            } catch (RuntimeException e) {
                SecurityContextHolder.clearContext();
                log.error("JWT processing failed internally", e);
                errors.write(response, 500, "系统异常，请稍后重试");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
