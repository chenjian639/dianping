package com.dianping.controller;

import com.dianping.common.PageResult;
import com.dianping.common.Result;
import com.dianping.dto.CreateOrderDTO;
import com.dianping.entity.Order;
import com.dianping.service.OrderService;
import com.dianping.vo.OrderDetailVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "订单接口")
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Result<Long> createOrder(
            @jakarta.validation.Valid @RequestBody CreateOrderDTO dto,
            HttpServletRequest request) {

        Long userId =
                (Long) request.getAttribute("userId");

        Long orderId =
                orderService.createOrder(
                        userId,
                        dto.getRequestId(), dto.getPickupSlotId()
                );

        return Result.success(orderId);
    }

    @GetMapping
    public Result<PageResult<Order>> getOrders(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        if (page < 1 || pageSize < 1 || pageSize > 100) throw new com.dianping.exception.BusinessException("分页参数不合法");
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