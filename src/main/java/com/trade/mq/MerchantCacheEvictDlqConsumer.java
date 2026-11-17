package com.trade.mq;

import com.trade.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MerchantCacheEvictDlqConsumer {

    private final RabbitTemplate rabbitTemplate;

    public MerchantCacheEvictDlqConsumer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(
            queues = RabbitMQConfig.MERCHANT_CACHE_EVICT_DLQ,
            autoStartup = "false",
            id = "merchantCacheEvictDlqListener"
    )
    public void handle(Long merchantId) {

        log.info(
                "开始重放DLQ缓存失效消息, merchantId={}",
                merchantId
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.MERCHANT_CACHE_EVICT_QUEUE,
                merchantId
        );

        log.info(
                "DLQ消息重新发送至业务队列成功, merchantId={}",
                merchantId
        );
    }
}