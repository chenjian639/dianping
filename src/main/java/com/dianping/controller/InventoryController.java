package com.dianping.controller;
import com.dianping.common.Result;
import com.dianping.service.InventoryService;
import com.dianping.security.MerchantAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/products/{id}/inventory")
public class InventoryController {
    private final InventoryService service;
    public record Adjustment(@NotBlank @Size(max=64) String requestId,@NotNull Integer delta) {}
    @PostMapping
    public Result<Void> adjust(@RequestAttribute(value="restaurantId",required=false) Long merchantId,
                               @PathVariable Long id,@Valid @RequestBody Adjustment body) {
        MerchantAccess.requireRestaurant(merchantId);
        service.adjust(merchantId,id,body.requestId(),body.delta());
        return Result.success(null);
    }
    @GetMapping
    public Result<?> history(@RequestAttribute(value="restaurantId",required=false) Long merchantId,@PathVariable Long id) {
        MerchantAccess.requireRestaurant(merchantId);
        return Result.success(service.history(merchantId,id));
    }
}
