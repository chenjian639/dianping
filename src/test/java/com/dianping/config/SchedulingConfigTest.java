package com.dianping.config;

import com.dianping.service.OrderTimeoutRecoveryService;
import com.dianping.service.OutboxPublisherService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulingConfigTest {
    @Test void blockedPublisherDoesNotBlockExpirationRecovery() throws Exception {
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch recovering = new CountDownLatch(1);
        OutboxPublisherService publisher = mock(OutboxPublisherService.class);
        OrderTimeoutRecoveryService orders = mock(OrderTimeoutRecoveryService.class);

        doAnswer(call -> { publishing.countDown();
            release.await(5,TimeUnit.SECONDS); return null; })
                .when(publisher).publishPending();

        doAnswer(call -> { recovering.countDown(); return null; })
                .when(orders).recoverExpiredOrders();

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(OutboxPublisherService.class, () -> publisher);
            context.registerBean(OrderTimeoutRecoveryService.class, () -> orders);
            context.register(SchedulingConfig.class);
            context.refresh();
            try {
                assertTrue(publishing.await(3,TimeUnit.SECONDS));
                assertTrue(recovering.await(3,TimeUnit.SECONDS), "Recovery must run while publishing is blocked");
            } finally { release.countDown(); }
        }
    }
}
