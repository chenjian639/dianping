package com.dianping.controller;

import com.dianping.common.PageResult;
import com.dianping.common.Result;
import com.dianping.dto.CreateDishDTO;
import com.dianping.dto.UpdateDishDTO;
import com.dianping.dto.UpdateDishStatusDTO;
import com.dianping.entity.Dish;
import com.dianping.exception.BusinessException;
import com.dianping.service.DishService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant/dishes")
public class MerchantDishController {

    @Autowired
    private DishService dishService;

    @PostMapping
    public Result<Long> createDish(
            @RequestAttribute("role") String role,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @Valid @RequestBody CreateDishDTO dto) {

        if (!"MERCHANT".equals(role)) {
            throw new BusinessException("仅商家可以操作菜品");
        }

        if (restaurantId == null) {
            throw new BusinessException("商家未绑定餐厅");
        }

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
            @RequestAttribute("role") String role,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @Valid @RequestBody UpdateDishDTO dto) {

        checkMerchant(role, restaurantId);

        dishService.updateDish(
                restaurantId,
                id,
                dto
        );

        return Result.success(null);
    }

    private void checkMerchant(
            String role,
            Long restaurantId) {

        if (!"MERCHANT".equals(role)) {
            throw new BusinessException("仅商家可以操作菜品");
        }

        if (restaurantId == null) {
            throw new BusinessException("商家未绑定餐厅");
        }
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateDishStatus(
            @PathVariable Long id,
            @RequestAttribute("role") String role,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @Valid @RequestBody UpdateDishStatusDTO dto) {

        checkMerchant(role, restaurantId);

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
            @RequestAttribute("role") String role,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        checkMerchant(role, restaurantId);

        dishService.deleteDish(
                restaurantId,
                id
        );

        return Result.success(null);
    }

    @GetMapping
    public Result<PageResult<Dish>> getMerchantDishes(
            @RequestAttribute("role") String role,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {

        checkMerchant(role, restaurantId);

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