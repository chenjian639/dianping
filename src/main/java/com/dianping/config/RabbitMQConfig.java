package com.dianping.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String LOCAL_CACHE_EXCHANGE = "restaurant.local-cache.evict";
    @Bean public FanoutExchange localCacheExchange() { return new FanoutExchange(LOCAL_CACHE_EXCHANGE); }
    @Bean public AnonymousQueue localCacheQueue() { return new AnonymousQueue(); }
    @Bean public Binding localCacheBinding(AnonymousQueue localCacheQueue, FanoutExchange localCacheExchange) {
        return BindingBuilder.bind(localCacheQueue).to(localCacheExchange);
    }


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

    // =========================
// 订单超时取消
// =========================

    public static final String ORDER_TIMEOUT_DELAY_EXCHANGE =
            "order.timeout.delay.exchange";

    public static final String ORDER_TIMEOUT_DELAY_QUEUE =
            "order.timeout.delay";

    public static final String ORDER_TIMEOUT_DELAY_ROUTING_KEY =
            "order.timeout.delay";

    public static final String ORDER_TIMEOUT_EXCHANGE =
            "order.timeout.exchange";

    public static final String ORDER_TIMEOUT_QUEUE =
            "order.timeout.check";

    public static final String ORDER_TIMEOUT_ROUTING_KEY =
            "order.timeout.check";

    public static final String ORDER_TIMEOUT_DLX =
            "order.timeout.dlx";

    public static final String ORDER_TIMEOUT_DLQ =
            "order.timeout.dlq";

    public static final String ORDER_TIMEOUT_DEAD_ROUTING_KEY =
            "order.timeout.dead";


    // ① 接收延迟消息的 exchange
    @Bean
    public DirectExchange orderTimeoutDelayExchange() {
        return new DirectExchange(
                ORDER_TIMEOUT_DELAY_EXCHANGE,
                true,
                false
        );
    }


    // ② 延迟队列：30分钟后自动死信到业务 Exchange
    @Bean
    public Queue orderTimeoutDelayQueue() {

        return QueueBuilder
                .durable(ORDER_TIMEOUT_DELAY_QUEUE)

                // 30分钟
                .ttl(30 * 60 * 1000)

                // TTL到期后送去哪
                .deadLetterExchange(
                        ORDER_TIMEOUT_EXCHANGE
                )
                .deadLetterRoutingKey(
                        ORDER_TIMEOUT_ROUTING_KEY
                )
                .build();
    }


    @Bean
    public Binding orderTimeoutDelayBinding() {

        return BindingBuilder
                .bind(orderTimeoutDelayQueue())
                .to(orderTimeoutDelayExchange())
                .with(ORDER_TIMEOUT_DELAY_ROUTING_KEY);
    }


    // ③ 真正处理超时订单的 Exchange
    @Bean
    public DirectExchange orderTimeoutExchange() {
        return new DirectExchange(
                ORDER_TIMEOUT_EXCHANGE,
                true,
                false
        );
    }


    // ④ 业务消费失败后的 DLX
    @Bean
    public DirectExchange orderTimeoutDlx() {
        return new DirectExchange(
                ORDER_TIMEOUT_DLX,
                true,
                false
        );
    }


    // ⑤ 真正被消费者监听的队列
    @Bean
    public Queue orderTimeoutQueue() {

        return QueueBuilder
                .durable(ORDER_TIMEOUT_QUEUE)
                .deadLetterExchange(
                        ORDER_TIMEOUT_DLX
                )
                .deadLetterRoutingKey(
                        ORDER_TIMEOUT_DEAD_ROUTING_KEY
                )
                .build();
    }


    @Bean
    public Binding orderTimeoutBinding() {

        return BindingBuilder
                .bind(orderTimeoutQueue())
                .to(orderTimeoutExchange())
                .with(ORDER_TIMEOUT_ROUTING_KEY);
    }


    // ⑥ 最终失败 DLQ
    @Bean
    public Queue orderTimeoutDlq() {

        return QueueBuilder
                .durable(ORDER_TIMEOUT_DLQ)
                .build();
    }


    @Bean
    public Binding orderTimeoutDlqBinding() {

        return BindingBuilder
                .bind(orderTimeoutDlq())
                .to(orderTimeoutDlx())
                .with(ORDER_TIMEOUT_DEAD_ROUTING_KEY);
    }
}