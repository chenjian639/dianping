package com.dianping.controller;

import com.dianping.common.Result;
import com.dianping.dto.UpdateRestaurantDTO;
import com.dianping.dto.UpdateRestaurantStatusDTO;
import com.dianping.exception.BusinessException;
import com.dianping.service.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant/restaurant")
public class MerchantRestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @PutMapping
    public Result<Void> updateRestaurant(
            @RequestAttribute("role") String role,
            @RequestAttribute("restaurantId") Long restaurantId,
            @Valid @RequestBody UpdateRestaurantDTO dto) {

        checkMerchant(role, restaurantId);

        restaurantService.updateRestaurant(
                restaurantId,
                dto
        );

        return Result.success(null);
    }

    private void checkMerchant(
            String role,
            Long restaurantId) {

        if (!"MERCHANT".equals(role)
                || restaurantId == null) {

            throw new BusinessException(
                    "仅商家可以访问"
            );
        }
    }

    @PutMapping("/status")
    public Result<Void> updateStatus(
            @RequestAttribute("role") String role,
            @RequestAttribute("restaurantId") Long restaurantId,
            @Valid @RequestBody UpdateRestaurantStatusDTO dto) {

        checkMerchant(role, restaurantId);

        restaurantService.updateRestaurantStatus(
                restaurantId,
                dto.getStatus()
        );

        return Result.success(null);
    }
}