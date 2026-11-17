package com.trade.mq;

import com.trade.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MerchantCacheEvictConsumer {

    private final StringRedisTemplate stringRedisTemplate;


    public MerchantCacheEvictConsumer(
            StringRedisTemplate stringRedisTemplate) {

        this.stringRedisTemplate =
                stringRedisTemplate;
    }

    @RabbitListener(
            queues = RabbitMQConfig.MERCHANT_CACHE_EVICT_QUEUE
    )
    public void handle(Long merchantId) {

        String key =
                "merchant:detail:" + merchantId;

        log.info(
                "收到缓存失效补偿消息, merchantId={}",
                merchantId
        );

        try {

            Boolean deleted =
                    stringRedisTemplate.delete(key);


            log.info(
                    "缓存补偿删除成功, merchantId={}, deleted={}",
                    merchantId,
                    deleted
            );

        } catch (Exception e) {

            log.error(
                    "缓存补偿删除失败, merchantId={}",
                    merchantId,
                    e
            );

            throw e;
        }
    }
}
