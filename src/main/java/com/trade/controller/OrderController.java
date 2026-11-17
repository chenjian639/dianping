package com.trade.controller;

import com.trade.vo.OrderSummaryVO;

import com.trade.common.PageResult;
import com.trade.common.Result;
import com.trade.dto.CreateOrderDTO;
import com.trade.service.OrderService;
import com.trade.vo.OrderDetailVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;


@Tag(name = "订单接口")
@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Result<Long> createOrder(
            @jakarta.validation.Valid @RequestBody CreateOrderDTO dto,
            HttpServletRequest request) {

        Long userId =
                (Long) request.getAttribute("userId");

        Long orderId =
                orderService.createOrder(
                        userId,
                        dto.getRequestId(), dto.getShippingAddress()
                );

        return Result.success(orderId);
    }

    @GetMapping
    public Result<PageResult<OrderSummaryVO>> getOrders(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        if (page < 1 || pageSize < 1 || pageSize > 100) throw new com.trade.exception.BusinessException("分页参数不合法");
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

    @PutMapping("/{id}/receive")
    public Result<Void> receive(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {

        orderService.receive(id, userId);

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