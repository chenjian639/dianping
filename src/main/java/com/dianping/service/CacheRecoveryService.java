package com.dianping.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CacheRecoveryService {

    private final RabbitListenerEndpointRegistry registry;

    public CacheRecoveryService(
            RabbitListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    public void startDlqReplay() {

        MessageListenerContainer container =
                registry.getListenerContainer(
                        "restaurantCacheEvictDlqListener"
                );

        if (container == null) {
            throw new IllegalStateException("DLQ Listener不存在");
        }

        if (!container.isRunning()) {
            container.start();
            log.info("DLQ重放Consumer已启动");
        }
    }

    public void stopDlqReplay() {

        MessageListenerContainer container =
                registry.getListenerContainer(
                        "restaurantCacheEvictDlqListener"
                );

        if (container != null && container.isRunning()) {
            container.stop();
            log.info("DLQ重放Consumer已停止");
        }
    }
}