package com.dianping.mq;

import com.dianping.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RestaurantCacheEvictDlqConsumer {

    private final RabbitTemplate rabbitTemplate;

    public RestaurantCacheEvictDlqConsumer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(
            queues = RabbitMQConfig.RESTAURANT_CACHE_EVICT_DLQ,
            autoStartup = "false",
            id = "restaurantCacheEvictDlqListener"
    )
    public void handle(Long restaurantId) {

        log.info(
                "开始重放DLQ缓存失效消息, restaurantId={}",
                restaurantId
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RESTAURANT_CACHE_EVICT_QUEUE,
                restaurantId
        );

        log.info(
                "DLQ消息重新发送至业务队列成功, restaurantId={}",
                restaurantId
        );
    }
}