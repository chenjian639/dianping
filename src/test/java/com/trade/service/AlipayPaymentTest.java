package com.trade.service;

import com.trade.entity.*;
import com.trade.mapper.*;
import com.trade.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlipayPaymentTest {
    final AlipayGateway gateway=mock(AlipayGateway.class);
    final OrderMapper orders=mock(OrderMapper.class);
    final PaymentMapper payments=mock(PaymentMapper.class);
    final PaymentService service=new PaymentService(gateway,orders,payments,
            mock(OrderItemMapper.class),mock(ProductMapper.class),mock(InventoryService.class));

    @Test void forgedNotificationCannotChangeState() {
        assertThrows(BusinessException.class,()->service.notifyPayment(Map.of("trade_status","TRADE_SUCCESS")));
        verifyNoInteractions(orders,payments);
    }

    @Test void verifiedLateNotificationSettlesBeforeTimeoutCanReleaseStock() {
        Payment payment=new Payment(); payment.setId(1L); payment.setOrderId(2L);
        payment.setAmount(new BigDecimal("15.00")); payment.setChannel("ALIPAY"); payment.setStatus("PENDING");
        Order order=new Order(); order.setId(2L); order.setUserId(3L); order.setStatus(0);
        when(gateway.verify(anyMap())).thenReturn(true);
        when(payments.byId(1L)).thenReturn(payment);
        when(payments.byOrder(2L)).thenReturn(payment);
        when(orders.lockById(2L)).thenReturn(order);
        when(orders.transition(2L,0,1)).thenReturn(1);
        when(payments.succeed(1L,"ali-ref")).thenReturn(1);
        service.notifyPayment(Map.of("trade_status","TRADE_SUCCESS","out_trade_no","trade-1",
                "trade_no","ali-ref","total_amount","15.00"));
        verify(orders).transition(2L,0,1);
    }

    @Test void uncertainChannelPreventsCancel() {
        Order order=new Order(); order.setId(2L);
        Payment payment=new Payment(); payment.setChannel("ALIPAY");
        when(payments.byOrder(2L)).thenReturn(payment);
        when(gateway.query(payment)).thenThrow(new BusinessException("unavailable"));
        assertThrows(BusinessException.class,()->service.closeBeforeCancel(order));
        verify(orders,never()).transition(any(),anyInt(),anyInt());
    }
}
