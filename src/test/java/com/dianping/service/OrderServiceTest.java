package com.dianping.service;

import com.dianping.enums.OrderStatus;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    @Test
    void payOrder_shouldSuccess_whenOrderIsPendingPayment() {

        Long userId = 3L;
        Long orderId = 100L;

        when(orderMapper.payOrder(
                orderId,
                userId,
                OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.PAID.getCode()
        )).thenReturn(1);

        orderService.payOrder(
                userId,
                orderId
        );

        verify(orderMapper, times(1))
                .payOrder(
                        orderId,
                        userId,
                        OrderStatus.PENDING_PAYMENT.getCode(),
                        OrderStatus.PAID.getCode()
                );
    }

    @Test
    void payOrder_shouldThrowException_whenStatusCannotPay() {

        Long userId = 3L;
        Long orderId = 100L;

        when(orderMapper.payOrder(
                orderId,
                userId,
                OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.PAID.getCode()
        )).thenReturn(0);

        assertThrows(
                BusinessException.class,
                () -> orderService.payOrder(
                        userId,
                        orderId
                )
        );
    }
}