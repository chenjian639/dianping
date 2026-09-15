package com.dianping.controller;

import com.dianping.common.Result;
import com.dianping.service.CacheRecoveryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/cache-recovery")
public class CacheRecoveryController {

    private final CacheRecoveryService cacheRecoveryService;

    public CacheRecoveryController(
            CacheRecoveryService cacheRecoveryService) {
        this.cacheRecoveryService = cacheRecoveryService;
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