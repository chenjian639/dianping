package com.dianping.service;

import com.dianping.config.RabbitMQConfig;
import com.dianping.entity.OutboxEvent;
import com.dianping.mapper.OutboxEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OutboxPublisherService {

    private final OutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisherService(
            OutboxEventMapper outboxEventMapper,
            RabbitTemplate rabbitTemplate) {

        this.outboxEventMapper = outboxEventMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPending() {

        List<OutboxEvent> events =
                outboxEventMapper.selectPending(20);

        for (OutboxEvent event : events) {

            try {

                CorrelationData correlationData =
                        new CorrelationData(event.getEventId());

                rabbitTemplate.convertAndSend(
                        "",
                        RabbitMQConfig.RESTAURANT_CACHE_EVICT_QUEUE,
                        event.getAggregateId(),
                        correlationData
                );

                CorrelationData.Confirm confirm =
                        correlationData.getFuture()
                                .get(5, TimeUnit.SECONDS);

                if (confirm.isAck()) {

                    log.warn(
                            "故障注入：Broker已经Confirm ACK，但故意不标记SENT, eventId={}",
                            event.getEventId()
                    );

                    throw new RuntimeException("模拟Confirm ACK后、markSent前应用宕机");

                } else {

                    outboxEventMapper.incrementRetry(event.getId());

                    log.error(
                            "Outbox Confirm NACK, eventId={}, reason={}",
                            event.getEventId(),
                            confirm.getReason()
                    );
                }

            } catch (Exception e) {

                outboxEventMapper.incrementRetry(event.getId());

                log.error(
                        "Outbox投递失败, eventId={}, error={}",
                        event.getEventId(),
                        e.getMessage()
                );
            }
        }
    }
}