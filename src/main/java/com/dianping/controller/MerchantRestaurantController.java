package com.dianping.controller;

import com.dianping.security.MerchantAccess;

import com.dianping.common.Result;
import com.dianping.dto.UpdateRestaurantDTO;
import com.dianping.dto.UpdateRestaurantStatusDTO;
import com.dianping.service.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/merchant/restaurant")
public class MerchantRestaurantController {

    private final RestaurantService restaurantService;

    @PutMapping
    public Result<Void> updateRestaurant(
            @RequestAttribute(value = "restaurantId", required = false) Long restaurantId,
            @Valid @RequestBody UpdateRestaurantDTO dto) {

        MerchantAccess.requireRestaurant(restaurantId);

        restaurantService.updateRestaurant(
                restaurantId,
                dto
        );

        return Result.success(null);
    }

    @PutMapping("/status")
    public Result<Void> updateStatus(
            @RequestAttribute(value = "restaurantId", required = false) Long restaurantId,
            @Valid @RequestBody UpdateRestaurantStatusDTO dto) {

        MerchantAccess.requireRestaurant(restaurantId);

        restaurantService.updateRestaurantStatus(
                restaurantId,
                dto.getStatus()
        );

        return Result.success(null);
    }
}
