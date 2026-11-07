package com.dianping.controller;

import com.dianping.common.Result;
import com.dianping.dto.CanteenDTO;
import com.dianping.dto.RestaurantCanteenDTO;
import com.dianping.entity.Canteen;
import com.dianping.service.CanteenGeoIndexService;
import com.dianping.service.CanteenService;
import com.dianping.service.RestaurantLocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "食堂管理", description = "仅ADMIN可用；示例坐标不是实际食堂位置")
@RestController
@RequestMapping("/admin")
public class AdminCanteenController {
    private final CanteenService service;
    private final CanteenGeoIndexService index;
    private final RestaurantLocationService locations;

    public AdminCanteenController(CanteenService service, CanteenGeoIndexService index,
                                  RestaurantLocationService locations) {
        this.service = service;
        this.index = index;
        this.locations = locations;
    }

    @GetMapping("/canteens")
    public Result<List<Canteen>> list() {
        return Result.success(service.list());
    }

    @Operation(summary = "创建食堂入口位置", description = "仅接受WGS84；正式数据和Outbox同事务写入")
    @PostMapping("/canteens")
    public Result<Long> create(@Valid @RequestBody CanteenDTO dto) {
        return Result.success(service.create(dto));
    }

    @Operation(summary = "完整更新食堂，包括启用/停用状态")
    @PutMapping("/canteens/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CanteenDTO dto) {
        service.update(id, dto);
        return Result.success(null);
    }

    @Operation(summary = "软删除食堂", description = "不删除档口、订单或历史关联；附近查询排除该食堂")
    @DeleteMapping("/canteens/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success(null);
    }

    @Operation(summary = "管理员内部重建GEO索引", description = "同步小数据集重建；不清其他Redis数据；失败时查询仍可回退")
    @PostMapping("/canteens/geo-index/rebuild")
    public Result<Void> rebuild() {
        index.rebuild();
        return Result.success(null);
    }

    @Operation(summary = "关联档口与食堂", description = "canteenId为null时解除关联；只有管理员可改变关联")
    @PutMapping("/restaurants/{id}/canteen")
    public Result<Void> bind(@PathVariable Long id, @Valid @RequestBody RestaurantCanteenDTO dto) {
        locations.bindCanteen(id, dto.getCanteenId());
        return Result.success(null);
    }
}
