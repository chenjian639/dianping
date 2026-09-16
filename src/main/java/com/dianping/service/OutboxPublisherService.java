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

            // 先抢占
            int claimed = outboxEventMapper.tryClaim(event.getId());

            if (claimed == 0) {
                log.info(
                        "Outbox事件已被其他实例抢占，跳过, eventId={}",
                        event.getEventId()
                );
                continue;
            }

            log.info(
                    "Outbox抢占成功, eventId={}, port={}",
                    event.getEventId()
            );

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
                    handleFailure(event,
                            "Confirm NACK: " + confirm.getReason());
                }

            } catch (Exception e) {

                handleFailure(event, e.getMessage());
            }
        }
    }

    private void handleFailure(
            OutboxEvent event,
            String reason) {

        int nextRetryCount = event.getRetryCount() + 1;

        if (nextRetryCount >= 5) {

            outboxEventMapper.markFailed(event.getId());

            log.error(
                    "Outbox超过最大重试次数，标记FAILED, eventId={}, reason={}",
                    event.getEventId(),
                    reason
            );

        } else {

            outboxEventMapper.releaseForRetry(event.getId());

            log.warn(
                    "Outbox投递失败，释放回PENDING等待重试, eventId={}, retryCount={}, reason={}",
                    event.getEventId(),
                    nextRetryCount,
                    reason
            );
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void recoverStuckEvents() {

        int recovered =
                outboxEventMapper.recoverStuckProcessing();

        if (recovered > 0) {
            log.warn(
                    "恢复超时PROCESSING Outbox事件, count={}",
                    recovered
            );
        }
    }
}