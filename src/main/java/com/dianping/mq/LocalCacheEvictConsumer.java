package com.dianping.mq;

import com.dianping.service.RestaurantLocalCacheService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LocalCacheEvictConsumer {
    private final RestaurantLocalCacheService cache;
    public LocalCacheEvictConsumer(RestaurantLocalCacheService cache) { this.cache = cache; }
    @RabbitListener(queues = "#{localCacheQueue.name}")
    public void handle(Long restaurantId) { cache.evict(restaurantId); }
}
