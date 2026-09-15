package com.dianping.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class CacheRecoveryService {

    private final RabbitListenerEndpointRegistry registry;
    private final RabbitTemplate rabbitTemplate;

    public CacheRecoveryService(
            RabbitListenerEndpointRegistry registry,
            RabbitTemplate rabbitTemplate) {

        this.registry = registry;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void startDlqReplay() {

        MessageListenerContainer container =
                registry.getListenerContainer(
                        "restaurantCacheEvictDlqListener"
                );

        if (container == null) {
            throw new IllegalStateException("找不到DLQ重放Listener");
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

    public void sendRoutingFailureTest() {

        String messageId = UUID.randomUUID().toString();

        CorrelationData correlationData =
                new CorrelationData(messageId);

        rabbitTemplate.convertAndSend(
                "",
                "this.queue.does.not.exist",
                1L,
                correlationData
        );

        log.info(
                "发送错误路由测试消息, messageId={}",
                messageId
        );
    }
}