package com.dianping.mq;

import com.dianping.config.RabbitMQConfig;
import com.dianping.service.CanteenGeoIndexService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CanteenGeoConsumer {
    private final CanteenGeoIndexService index;

    public CanteenGeoConsumer(CanteenGeoIndexService index) {
        this.index = index;
    }

    @RabbitListener(queues = RabbitMQConfig.CANTEEN_GEO_QUEUE)
    public void handle(Long canteenId) {
        // Events are invalidation hints, not coordinate snapshots. Delayed events read current state.
        index.rebuild();
    }
}
