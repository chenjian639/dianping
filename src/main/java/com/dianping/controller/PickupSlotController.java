package com.dianping.controller;
import com.dianping.common.Result;
import com.dianping.dto.CreatePickupSlotDTO;
import com.dianping.entity.PickupSlot;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.PickupSlotMapper;
import com.dianping.service.PickupSlotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
public class PickupSlotController {
    private final PickupSlotMapper mapper;
    private final PickupSlotService service;
    public PickupSlotController(PickupSlotMapper mapper, PickupSlotService service) {
        this.mapper = mapper; this.service = service;
    }
    @GetMapping("/restaurants/{id}/pickup-slots")
    public Result<List<PickupSlot>> available(@PathVariable Long id) {
        return Result.success(mapper.selectAvailable(id));
    }
    @PostMapping("/merchant/pickup-slots")
    public Result<Long> create(@RequestAttribute("role") String role,
            @RequestAttribute(value="restaurantId", required=false) Long restaurantId,
            @Valid @RequestBody CreatePickupSlotDTO dto) {
        if (!"MERCHANT".equals(role) || restaurantId == null) throw new BusinessException("仅绑定档口的商家可以创建时段");
        return Result.success(service.create(restaurantId, dto));
    }
}
