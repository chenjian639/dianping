package com.trade.controller;

import com.trade.security.MerchantAccess;

import com.trade.common.Result;
import com.trade.dto.UpdateMerchantDTO;
import com.trade.dto.UpdateMerchantStatusDTO;
import com.trade.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/merchant/profile")
public class MerchantProfileController {

    private final MerchantService merchantService;

    @PutMapping
    public Result<Void> updateMerchant(
            @RequestAttribute(value = "merchantId", required = false) Long merchantId,
            @Valid @RequestBody UpdateMerchantDTO dto) {

        MerchantAccess.requireMerchant(merchantId);

        merchantService.updateMerchant(
                merchantId,
                dto
        );

        return Result.success(null);
    }

    @PutMapping("/status")
    public Result<Void> updateStatus(
            @RequestAttribute(value = "merchantId", required = false) Long merchantId,
            @Valid @RequestBody UpdateMerchantStatusDTO dto) {

        MerchantAccess.requireMerchant(merchantId);

        merchantService.updateMerchantStatus(
                merchantId,
                dto.getStatus()
        );

        return Result.success(null);
    }
}
