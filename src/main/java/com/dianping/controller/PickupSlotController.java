package com.dianping.controller;

import com.dianping.security.MerchantAccess;
import com.dianping.common.Result;
import com.dianping.dto.CreatePickupSlotDTO;
import com.dianping.entity.PickupSlot;
import com.dianping.service.PickupSlotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
public class PickupSlotController {
    private final PickupSlotService service;
    public PickupSlotController(PickupSlotService service) {
        this.service = service;
    }
    @GetMapping("/restaurants/{id}/pickup-slots")
    public Result<List<PickupSlot>> available(@PathVariable Long id) {

        return Result.success(service.available(id));
    }

    @PostMapping("/merchant/pickup-slots")
    public Result<Long> create(
            @RequestAttribute(value="restaurantId", required=false) Long restaurantId,
            @Valid @RequestBody CreatePickupSlotDTO dto) {
        MerchantAccess.requireRestaurant(restaurantId);
        return Result.success(service.create(restaurantId, dto));
    }
}
