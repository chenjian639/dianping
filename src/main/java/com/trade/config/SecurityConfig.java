package com.trade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.trade.security.ApiErrorWriter;
import com.trade.security.JwtAuthenticationFilter;
import com.trade.util.JwtUtil;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtUtil jwt, ApiErrorWriter errors) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(handlers -> handlers
                        .authenticationEntryPoint((request, response, e) -> errors.write(response, 401, "请先登录"))
                        .accessDeniedHandler((request, response, e) -> errors.write(response, 403, "无权访问")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/ui/**", "/favicon.ico", "/payments/alipay/notify", "/users/login", "/users/register", "/merchants/search",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/merchant/**").hasRole("MERCHANT")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwt, errors), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
