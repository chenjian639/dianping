package com.trade.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class MerchantCacheService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 解锁脚本：只有value仍为当前持有者时才删除，
     * 避免锁过期后误删其他线程新抢到的锁。
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] "
                            + "then return redis.call('del', KEYS[1]) "
                            + "else return 0 end",
                    Long.class
            );

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

    /**
     * 尝试获取缓存重建互斥锁（SET key value NX EX 原子操作）。
     * 抢到锁的线程负责回源数据库并回填缓存。
     */
    @CircuitBreaker(name = "redisCache")
    public boolean tryLock(
            String lockKey,
            String owner,
            long timeout,
            TimeUnit unit) {

        Boolean locked =
                stringRedisTemplate
                        .opsForValue()
                        .setIfAbsent(
                                lockKey,
                                owner,
                                timeout,
                                unit
                        );

        return Boolean.TRUE.equals(locked);
    }

    /**
     * 释放互斥锁；锁已过期或易主时脚本返回0，不会删除他人的锁。
     */
    @CircuitBreaker(name = "redisCache")
    public void unlock(String lockKey, String owner) {

        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                List.of(lockKey),
                owner
        );
    }
}