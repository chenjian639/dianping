package com.dianping.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RestaurantCacheService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @CircuitBreaker(name = "redisCache")
    public String get(String key) {

        return stringRedisTemplate
                .opsForValue()
                .get(key);
    }

    @CircuitBreaker(name = "redisCache")
    public void set(
            String key,
            String value,
            long timeout,
            TimeUnit unit) {

        stringRedisTemplate
                .opsForValue()
                .set(
                        key,
                        value,
                        timeout,
                        unit
                );
    }

    @CircuitBreaker(name = "redisCache")
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }
}