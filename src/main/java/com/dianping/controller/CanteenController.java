package com.dianping.controller;

import com.dianping.common.Result;
import com.dianping.dto.NearbyCanteenQuery;
import com.dianping.service.NearbyCanteenService;
import com.dianping.vo.NearbyCanteenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "食堂位置", description = "登录后查询；不保存本次参考位置")
@RestController
@RequestMapping("/canteens")
public class CanteenController {
    private final NearbyCanteenService service;

    public CanteenController(NearbyCanteenService service) {
        this.service = service;
    }

    @Operation(summary = "查询附近启用食堂", description = "WGS84坐标，按入口直线距离升序；不是步行距离，同距按ID排序")
    @PostMapping("/nearby")
    public Result<List<NearbyCanteenVO>> nearby(@Valid @RequestBody NearbyCanteenQuery query) {
        return Result.success(service.nearby(query));
    }
}
