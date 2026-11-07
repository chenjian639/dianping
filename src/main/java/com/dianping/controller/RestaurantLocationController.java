package com.dianping.controller;

import com.dianping.common.Result;
import com.dianping.dto.RestaurantLocationDTO;
import com.dianping.security.MerchantAccess;
import com.dianping.service.RestaurantLocationService;
import com.dianping.vo.RestaurantLocationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "档口取餐位置")
@RestController
public class RestaurantLocationController {
    private final RestaurantLocationService service;

    public RestaurantLocationController(RestaurantLocationService service) {
        this.service = service;
    }

    @Operation(summary = "查询档口取餐位置", description = "需要登录；未关联食堂的旧档口也可查询")
    @GetMapping("/restaurants/{id}/location")
    public Result<RestaurantLocationVO> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @Operation(summary = "商家更新自己档口的楼层、窗口号与取餐说明", description = "不接受客户端选择档口或修改食堂坐标/关联")
    @PutMapping("/merchant/restaurant/location")
    public Result<Void> update(
            @RequestAttribute(value = "restaurantId", required = false) Long restaurantId,
            @Valid @RequestBody RestaurantLocationDTO dto) {
        MerchantAccess.requireRestaurant(restaurantId);
        service.update(restaurantId, dto);
        return Result.success(null);
    }
}
