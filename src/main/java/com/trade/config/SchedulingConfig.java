package com.trade.config;

import com.trade.service.OrderTimeoutRecoveryService;
import com.trade.service.OutboxPublisherService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.IntervalTask;

/** Slow publisher confirms must not occupy the order-expiration recovery thread. */
@Configuration
public class SchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler outboxScheduler() { return scheduler("outbox-"); }

    @Bean
    public ThreadPoolTaskScheduler recoveryScheduler() { return scheduler("recovery-"); }

    private ThreadPoolTaskScheduler scheduler(String prefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean
    public ScheduledTaskRegistrar outboxTasks(
            @Qualifier("outboxScheduler") ThreadPoolTaskScheduler scheduler,
            OutboxPublisherService publisher) {
        ScheduledTaskRegistrar tasks = new ScheduledTaskRegistrar();
        tasks.setTaskScheduler(scheduler);
        tasks.addFixedDelayTask(publisher::publishPending, 5000);
        return tasks;
    }

    @Bean
    public ScheduledTaskRegistrar recoveryTasks(
            @Qualifier("recoveryScheduler") ThreadPoolTaskScheduler scheduler,
            OutboxPublisherService publisher, OrderTimeoutRecoveryService orders, com.trade.service.RefundService refunds) {
        ScheduledTaskRegistrar tasks = new ScheduledTaskRegistrar();
        tasks.setTaskScheduler(scheduler);
        tasks.addFixedDelayTask(publisher::recoverStuckEvents, 30000);
        tasks.addFixedDelayTask(orders::recoverExpiredOrders, 60000);
        tasks.addFixedDelayTask(refunds::recover, 60000);
        return tasks;
    }
}
