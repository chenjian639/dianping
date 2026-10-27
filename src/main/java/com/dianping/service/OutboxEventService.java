package com.dianping.service;

import com.dianping.entity.OutboxEvent;
import com.dianping.event.RestaurantChangedEvent;
import com.dianping.mapper.OutboxEventMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Record durable events inside the caller's business transaction. Never contacts MQ/Redis. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OutboxEventService {
    private final OutboxEventMapper mapper;
    private final ApplicationEventPublisher events;

    public OutboxEventService(OutboxEventMapper mapper, ApplicationEventPublisher events) {
        this.mapper = mapper;
        this.events = events;
    }

    public void restaurantChanged(Long restaurantId) {
        record("RESTAURANT_CACHE_EVICT", restaurantId);
        events.publishEvent(new RestaurantChangedEvent(restaurantId));
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
