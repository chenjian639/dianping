package com.dianping.controller;

import com.dianping.security.MerchantAccess;

import com.dianping.common.PageResult;
import com.dianping.common.Result;
import com.dianping.dto.CreateDishDTO;
import com.dianping.dto.UpdateDishDTO;
import com.dianping.dto.UpdateDishStatusDTO;
import com.dianping.entity.Dish;
import com.dianping.exception.BusinessException;
import com.dianping.service.DishService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/merchant/dishes")
public class MerchantDishController {

    private final DishService dishService;

    @PostMapping
    public Result<Long> createDish(
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @Valid @RequestBody CreateDishDTO dto) {

        MerchantAccess.requireRestaurant(restaurantId);

        return Result.success(
                dishService.createDish(
                        restaurantId,
                        dto
                )
        );
    }

    @PutMapping("/{id}")
    public Result<Void> updateDish(
            @PathVariable Long id,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @Valid @RequestBody UpdateDishDTO dto) {

        MerchantAccess.requireRestaurant(restaurantId);

        dishService.updateDish(
                restaurantId,
                id,
                dto
        );

        return Result.success(null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateDishStatus(
            @PathVariable Long id,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @Valid @RequestBody UpdateDishStatusDTO dto) {

        MerchantAccess.requireRestaurant(restaurantId);

        dishService.updateDishStatus(
                restaurantId,
                id,
                dto.getStatus()
        );

        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteDish(
            @PathVariable Long id,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        MerchantAccess.requireRestaurant(restaurantId);

        dishService.deleteDish(
                restaurantId,
                id
        );

        return Result.success(null);
    }

    @GetMapping
    public Result<PageResult<Dish>> getMerchantDishes(
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {

        MerchantAccess.requireRestaurant(restaurantId);

        return Result.success(
                dishService.getMerchantDishes(
                        restaurantId,
                        page,
                        pageSize,
                        status
                )
        );
    }
}