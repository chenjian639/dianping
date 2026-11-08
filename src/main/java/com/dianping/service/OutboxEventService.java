package com.dianping.service;

import com.dianping.entity.OutboxEvent;

import com.dianping.mapper.OutboxEventMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Record durable events inside the caller's business transaction. Never contacts MQ/Redis. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OutboxEventService {
    private final OutboxEventMapper mapper;


    public OutboxEventService(OutboxEventMapper mapper) {
        this.mapper = mapper;

    }

    public void restaurantChanged(Long restaurantId) {
        record("RESTAURANT_CACHE_EVICT", restaurantId);

    }

    public void orderTimeout(Long orderId) {
        record("ORDER_TIMEOUT_CHECK", orderId);
    }


    private void record(String type, Long aggregateId) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(type);
        event.setAggregateId(aggregateId);
        event.setPayload(String.valueOf(aggregateId));
        event.setStatus("PENDING");
        event.setRetryCount(0);
        mapper.insert(event);
    }
}
