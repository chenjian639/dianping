package com.dianping.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String RESTAURANT_CACHE_EVICT_QUEUE =
            "restaurant.cache.evict";

    @Bean
    public Queue restaurantCacheEvictQueue() {
        return new Queue(
                RESTAURANT_CACHE_EVICT_QUEUE,
                true
        );
    }
}