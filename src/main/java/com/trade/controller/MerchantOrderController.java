package com.trade.controller;

import jakarta.validation.Valid;


import com.trade.vo.OrderSummaryVO;

import com.trade.security.MerchantAccess;

import com.trade.common.PageResult;
import com.trade.common.Result;
import com.trade.exception.BusinessException;
import com.trade.service.OrderService;
import com.trade.vo.OrderDetailVO;
import org.springframework.web.bind.annotation.*;


@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/merchant/orders")
public class MerchantOrderController {

    private final OrderService orderService;

    @GetMapping
    public Result<PageResult<OrderSummaryVO>> getOrders(
            @RequestAttribute(value = "merchantId", required = false)
            Long merchantId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {

        MerchantAccess.requireMerchant(merchantId);

        if (page < 1) {
            throw new BusinessException("page不能小于1");
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException("pageSize必须在1到100之间");
        }

        if (status != null && (status < 0 || status > 6)) {
            throw new BusinessException("订单状态不合法");
        }

        return Result.success(
                orderService.getMerchantOrders(
                        merchantId,
                        page,
                        pageSize,
                        status
                )
        );
    }

    @PutMapping("/{id}/ship")
    public Result<Void> ship(
            @PathVariable Long id,
            @RequestAttribute(value = "merchantId", required = false)
            Long merchantId) {

        MerchantAccess.requireMerchant(merchantId);

        orderService.ship(id, merchantId);

        return Result.success(null);
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> getOrderDetail(
            @PathVariable Long id,
            @RequestAttribute(value = "merchantId", required = false)
            Long merchantId) {

        MerchantAccess.requireMerchant(merchantId);

        return Result.success(
                orderService.getMerchantOrderDetail(
                        merchantId,
                        id
                )
        );
    }
}