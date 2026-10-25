package com.dianping.controller;

import com.dianping.common.Result;
import com.dianping.service.CacheRecoveryService;
import com.dianping.service.OutboxPublisherService;
import org.springframework.web.bind.annotation.*;

@RestController
@com.dianping.interceptor.AdminOnly
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

}
