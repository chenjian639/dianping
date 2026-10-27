package com.dianping.service;

import com.dianping.enums.OrderStatus;
import com.dianping.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class OrderTimeoutRecoveryService {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    public OrderTimeoutRecoveryService(
            OrderMapper orderMapper,
            OrderService orderService) {

        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    public void recoverExpiredOrders() {

        List<Long> orderIds =
                orderMapper
                        .selectExpiredPendingOrderIds(
                                OrderStatus
                                        .PENDING_PAYMENT
                                        .getCode(),
                                100
                        );

        for (Long orderId : orderIds) {

            try {

                orderService.timeoutCancelOrder(
                        orderId
                );

            } catch (Exception e) {

                log.error(
                        "兜底取消超时订单失败, orderId={}",
                        orderId,
                        e
                );
            }
        }
    }
}