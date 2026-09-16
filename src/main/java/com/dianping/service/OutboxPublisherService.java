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

                    outboxEventMapper.markSent(event.getId());

                    log.info(
                            "Outbox投递成功并标记SENT, eventId={}",
                            event.getEventId()
                    );

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