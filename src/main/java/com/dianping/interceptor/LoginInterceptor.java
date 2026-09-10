package com.dianping.interceptor;

import com.dianping.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    private final JwtUtil jwtUtil;
    public LoginInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                {
                    "code": 401,
                    "msg": "未登录或token不存在",
                    "data": null
                }
                """);
            return false;
        }

        String token = authorization.substring(7);

        try {
            Claims claims = jwtUtil.parseToken(token);

            Long userId = Long.valueOf(claims.getSubject());
            request.setAttribute("userId", userId);
            String role = claims.get("role", String.class);
            request.setAttribute("role", role);

            Number restaurantIdNumber = claims.get("restaurantId", Number.class);
            Long restaurantId = restaurantIdNumber == null ? null : restaurantIdNumber.longValue();

            request.setAttribute("restaurantId", restaurantId);
            System.out.println("当前用户ID：" + userId);

            return true;

        } catch (Exception e) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                {
                    "code": 401,
                    "msg": "token无效或已过期",
                    "data": null
                }
                """);

            return false;
        }
    }
}