package com.dianping.service;

import com.dianping.config.RabbitMQConfig;
import com.dianping.entity.OutboxEvent;
import com.dianping.mapper.OutboxEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OutboxPublisherService {

    private final OutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingScheduled() {
        publishPending();
    }

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

                int retryCount = event.getRetryCount() + 1;

                if (retryCount >= 5) {
                    outboxEventMapper.markFailed(event.getId());

                    log.error(
                            "Outbox超过最大重试次数，标记FAILED, eventId={}",
                            event.getEventId()
                    );
                } else {
                    outboxEventMapper.incrementRetry(event.getId());
                }
            }
        }
    }
}