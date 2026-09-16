package com.dianping.controller;

import com.dianping.common.Result;
import com.dianping.service.CacheRecoveryService;
import com.dianping.service.OutboxPublisherService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/cache-recovery")
public class CacheRecoveryController {

    private final CacheRecoveryService cacheRecoveryService;
    private final OutboxPublisherService outboxPublisherService;

    public CacheRecoveryController(
            CacheRecoveryService cacheRecoveryService, OutboxPublisherService outboxPublisherService) {
        this.cacheRecoveryService = cacheRecoveryService;
        this.outboxPublisherService = outboxPublisherService;
    }

    @PostMapping("/start")
    public Result<String> start() {

        cacheRecoveryService.startDlqReplay();

        return Result.success("DLQ重放已启动");
    }

    @PostMapping("/stop")
    public Result<String> stop() {

        cacheRecoveryService.stopDlqReplay();

        return Result.success("DLQ重放已停止");
    }

    @PostMapping("/test-routing-failure")
    public Result<String> testRoutingFailure() {

        cacheRecoveryService.sendRoutingFailureTest();

        return Result.success("routing failure test sent");
    }

    @PostMapping("/outbox/publish")
    public Result<String> publishOutbox() {

        outboxPublisherService.publishPending();

        return Result.success("outbox publish finished");
    }
}