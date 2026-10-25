package com.dianping.service;

import com.dianping.config.RabbitMQConfig;
import com.dianping.mq.LocalCacheEvictConsumer;
import com.dianping.mq.RestaurantCacheEvictConsumer;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.mockito.Mockito.*;

class CacheBroadcastTest {
    @Test void redisDeletionPrecedesBroadcastAndEachConsumerEvictsLocalCache() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RabbitTemplate mq = mock(RabbitTemplate.class);
        new RestaurantCacheEvictConsumer(redis,mq).handle(1L);
        InOrder order = inOrder(redis,mq);
        order.verify(redis).delete("restaurant:detail:1");
        order.verify(mq).convertAndSend(RabbitMQConfig.LOCAL_CACHE_EXCHANGE,"",1L);
        RestaurantLocalCacheService first = mock(RestaurantLocalCacheService.class);
        RestaurantLocalCacheService second = mock(RestaurantLocalCacheService.class);
        new LocalCacheEvictConsumer(first).handle(1L);
        new LocalCacheEvictConsumer(second).handle(1L);
        verify(first).evict(1L); verify(second).evict(1L);
    }
}
