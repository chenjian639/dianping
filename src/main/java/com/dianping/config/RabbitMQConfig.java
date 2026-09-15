package com.dianping.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String RESTAURANT_CACHE_EVICT_QUEUE =
            "restaurant.cache.evict";

    public static final String RESTAURANT_CACHE_EVICT_DLX =
            "restaurant.cache.evict.dlx";

    public static final String RESTAURANT_CACHE_EVICT_DLQ =
            "restaurant.cache.evict.dlq";

    public static final String RESTAURANT_CACHE_EVICT_DLQ_ROUTING_KEY =
            "restaurant.cache.evict.dead";

    @Bean
    public DirectExchange restaurantCacheEvictDlx() {
        return new DirectExchange(
                RESTAURANT_CACHE_EVICT_DLX,
                true,
                false
        );
    }

    @Bean
    public Queue restaurantCacheEvictQueue() {

        return QueueBuilder
                .durable(RESTAURANT_CACHE_EVICT_QUEUE)
                .deadLetterExchange(RESTAURANT_CACHE_EVICT_DLX)
                .deadLetterRoutingKey(
                        RESTAURANT_CACHE_EVICT_DLQ_ROUTING_KEY
                )
                .build();
    }

    @Bean
    public Queue restaurantCacheEvictDlq() {
        return QueueBuilder
                .durable(RESTAURANT_CACHE_EVICT_DLQ)
                .build();
    }

    @Bean
    public Binding restaurantCacheEvictDlqBinding() {

        return BindingBuilder
                .bind(restaurantCacheEvictDlq())
                .to(restaurantCacheEvictDlx())
                .with(RESTAURANT_CACHE_EVICT_DLQ_ROUTING_KEY);
    }
}