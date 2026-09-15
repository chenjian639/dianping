package com.dianping.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitTemplateConfig {

    public RabbitTemplateConfig(RabbitTemplate rabbitTemplate) {

        // Producer → Broker
        rabbitTemplate.setConfirmCallback(
                (CorrelationData correlationData,
                 boolean ack,
                 String cause) -> {

                    String id = correlationData == null
                            ? null
                            : correlationData.getId();

                    if (ack) {
                        log.info(
                                "RabbitMQ Confirm ACK, messageId={}",
                                id
                        );
                    } else {
                        log.error(
                                "RabbitMQ Confirm NACK, messageId={}, cause={}",
                                id,
                                cause
                        );
                    }
                }
        );

        // Exchange → Queue
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error(
                    "RabbitMQ消息无法路由: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText()
            );
        });
    }
}