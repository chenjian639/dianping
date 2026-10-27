package com.dianping.controller;

import jakarta.validation.Valid;
import com.dianping.dto.PickupVerificationDTO;

import com.dianping.vo.OrderSummaryVO;

import com.dianping.security.MerchantAccess;

import com.dianping.common.PageResult;
import com.dianping.common.Result;
import com.dianping.exception.BusinessException;
import com.dianping.service.OrderService;
import com.dianping.vo.OrderDetailVO;
import org.springframework.web.bind.annotation.*;


@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/merchant/orders")
public class MerchantOrderController {

    private final OrderService orderService;

    @GetMapping
    public Result<PageResult<OrderSummaryVO>> getOrders(
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {

        MerchantAccess.requireRestaurant(restaurantId);

        if (page < 1) {
            throw new BusinessException("page不能小于1");
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException("pageSize必须在1到100之间");
        }

        if (status != null && (status < 0 || status > 5)) {
            throw new BusinessException("订单状态不合法");
        }

        return Result.success(
                orderService.getMerchantOrders(
                        restaurantId,
                        page,
                        pageSize,
                        status
                )
        );
    }

    @PutMapping("/{id}/prepare")
    public Result<Void> startPreparing(
            @PathVariable Long id,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        MerchantAccess.requireRestaurant(restaurantId);

        orderService.startPreparing(id, restaurantId);

        return Result.success(null);
    }

    @PutMapping("/{id}/ready")
    public Result<Void> markReady(
            @PathVariable Long id,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        MerchantAccess.requireRestaurant(restaurantId);

        orderService.markReady(id, restaurantId);

        return Result.success(null);
    }

    @PutMapping("/{id}/pickup")
    public Result<Void> completeOrder(
            @PathVariable Long id,
            @Valid @RequestBody PickupVerificationDTO dto,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        MerchantAccess.requireRestaurant(restaurantId);

        orderService.completeOrder(id, restaurantId, dto.getPickupCode());

        return Result.success(null);
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> getOrderDetail(
            @PathVariable Long id,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        MerchantAccess.requireRestaurant(restaurantId);

        return Result.success(
                orderService.getMerchantOrderDetail(
                        restaurantId,
                        id
                )
        );
    }
}