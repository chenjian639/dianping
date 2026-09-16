package com.dianping.listener;

import com.dianping.event.RestaurantChangedEvent;
import com.dianping.service.RestaurantLocalCacheService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RestaurantLocalCacheInvalidationListener {

    private final RestaurantLocalCacheService localCacheService;

    public RestaurantLocalCacheInvalidationListener(
            RestaurantLocalCacheService localCacheService) {

        this.localCacheService =
                localCacheService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            RestaurantChangedEvent event) {

        localCacheService.evict(
                event.restaurantId()
        );
    }
}