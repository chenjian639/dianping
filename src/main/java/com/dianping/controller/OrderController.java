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
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public Result<Long> createOrder(
            @RequestAttribute("userId") Long userId) {

        Long orderId = orderService.createOrder(userId);

        return Result.success(orderId);
    }

    @GetMapping
    public Result<PageResult<Order>> getOrders(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        return Result.success(
                orderService.getOrders(userId, page, pageSize)
        );
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> getOrderDetail(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {

        return Result.success(
                orderService.getOrderDetail(userId, id)
        );
    }

    @PutMapping("/{id}/pay")
    public Result<Void> payOrder(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {

        orderService.payOrder(userId, id);

        return Result.success(null);
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancelOrder(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {

        orderService.cancelOrder(userId, id);

        return Result.success(null);
    }
}