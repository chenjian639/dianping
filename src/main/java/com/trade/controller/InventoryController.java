package com.trade.controller;
import com.trade.common.Result;
import com.trade.service.InventoryService;
import com.trade.security.MerchantAccess;
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
    public Result<Void> adjust(@RequestAttribute(value="merchantId",required=false) Long merchantId,
                               @PathVariable Long id,@Valid @RequestBody Adjustment body) {
        MerchantAccess.requireMerchant(merchantId);
        service.adjust(merchantId,id,body.requestId(),body.delta());
        return Result.success(null);
    }
    @GetMapping
    public Result<?> history(@RequestAttribute(value="merchantId",required=false) Long merchantId,@PathVariable Long id) {
        MerchantAccess.requireMerchant(merchantId);
        return Result.success(service.history(merchantId,id));
    }
}
