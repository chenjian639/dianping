package com.dianping.controller;

import com.dianping.common.PageResult;
import com.dianping.common.Result;
import com.dianping.entity.Order;
import com.dianping.exception.BusinessException;
import com.dianping.service.OrderService;
import com.dianping.vo.OrderDetailVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/merchant/orders")
public class MerchantOrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping
    public Result<PageResult<Order>> getOrders(
            @RequestAttribute("role") String role,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {

        checkMerchant(role, restaurantId);

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
            @RequestAttribute("role") String role,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        checkMerchant(role, restaurantId);

        orderService.startPreparing(id, restaurantId);

        return Result.success(null);
    }

    @PutMapping("/{id}/ready")
    public Result<Void> markReady(
            @PathVariable Long id,
            @RequestAttribute("role") String role,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        checkMerchant(role, restaurantId);

        orderService.markReady(id, restaurantId);

        return Result.success(null);
    }

    @PutMapping("/{id}/pickup")
    public Result<Void> completeOrder(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.dianping.dto.PickupVerificationDTO dto,
            @RequestAttribute("role") String role,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        checkMerchant(role, restaurantId);

        orderService.completeOrder(id, restaurantId, dto.getPickupCode());

        return Result.success(null);
    }

    private void checkMerchant(
            String role,
            Long restaurantId) {

        if (!"MERCHANT".equals(role)) {
            throw new BusinessException("仅商家可以访问");
        }

        if (restaurantId == null) {
            throw new BusinessException("商家未绑定餐厅");
        }
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> getOrderDetail(
            @PathVariable Long id,
            @RequestAttribute("role") String role,
            @RequestAttribute(value = "restaurantId", required = false)
            Long restaurantId) {

        checkMerchant(role, restaurantId);

        return Result.success(
                orderService.getMerchantOrderDetail(
                        restaurantId,
                        id
                )
        );
    }
}