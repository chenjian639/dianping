package com.dianping.mq;

import com.dianping.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RestaurantCacheEvictConsumer {

    private final StringRedisTemplate stringRedisTemplate;

    public RestaurantCacheEvictConsumer(
            StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate =
                stringRedisTemplate;
    }

    @RabbitListener(
            queues = RabbitMQConfig.RESTAURANT_CACHE_EVICT_QUEUE
    )
    public void handle(Long restaurantId) {

        String key =
                "restaurant:detail:" + restaurantId;

        log.info(
                "收到缓存失效补偿消息, restaurantId={}",
                restaurantId
        );

        try {

            Boolean deleted =
                    stringRedisTemplate.delete(key);

            log.info(
                    "缓存补偿删除成功, restaurantId={}, deleted={}",
                    restaurantId,
                    deleted
            );

        } catch (Exception e) {

            log.error(
                    "缓存补偿删除失败, restaurantId={}",
                    restaurantId,
                    e
            );

            throw e;
        }
    }
}